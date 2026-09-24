//! Raw ESC/POS output for thermal printers.
//!
//! The browser can only print through a dialog and a printer driver. A thermal
//! printer is normally driven by writing ESC/POS bytes at it directly, which
//! prints with no dialog and can kick the cash drawer - so that transport lives
//! here, in the desktop shell, and the web build falls back to the dialog.
//!
//! The bytes themselves are built in TypeScript (`src/lib/escpos.ts`) where they
//! are unit tested; this module only has to deliver them unaltered. Three ways to
//! reach a printer are supported, chosen by what the target looks like:
//!
//! * `tcp://192.168.1.50:9100` - a network/Ethernet printer speaking RAW (the
//!   near-universal JetDirect port).
//! * `\\\\localhost\\POS-80` - a Windows printer share. Sharing the printer once
//!   makes it writable as a file, which avoids binding the winspool API.
//! * anything else on Unix - passed to `lp -d <name> -o raw`.
//!
//! Nothing here interprets the payload: a byte the encoder produced is a byte the
//! printer receives, because ESC/POS is position sensitive and a helpful
//! conversion (line endings especially) would corrupt the job.

use std::io::Write;
use std::net::TcpStream;
use std::time::Duration;

const CONNECT_TIMEOUT: Duration = Duration::from_secs(5);

/// Writes raw bytes to the given printer. An empty target means the OS default.
#[tauri::command]
pub fn print_raw(printer_name: String, data: Vec<u8>) -> Result<(), String> {
    let target = printer_name.trim();
    if data.is_empty() {
        return Err("Nothing to print".to_string());
    }
    if let Some(address) = target.strip_prefix("tcp://") {
        return print_to_socket(address, &data);
    }
    if target.starts_with("\\\\") {
        return print_to_share(target, &data);
    }
    print_to_spooler(target, &data)
}

fn print_to_socket(address: &str, data: &[u8]) -> Result<(), String> {
    let resolved = address
        .to_socket_addrs_compat()
        .ok_or_else(|| format!("Could not understand the printer address '{address}'"))?;
    let mut stream = TcpStream::connect_timeout(&resolved, CONNECT_TIMEOUT)
        .map_err(|e| format!("Could not reach the printer at {address}: {e}"))?;
    stream
        .set_write_timeout(Some(CONNECT_TIMEOUT))
        .map_err(|e| e.to_string())?;
    stream
        .write_all(data)
        .map_err(|e| format!("The printer at {address} stopped accepting the receipt: {e}"))?;
    stream.flush().map_err(|e| e.to_string())
}

/// A shared Windows printer is writable as a file, so no winspool binding is needed.
fn print_to_share(share: &str, data: &[u8]) -> Result<(), String> {
    let mut file = std::fs::OpenOptions::new()
        .write(true)
        .open(share)
        .map_err(|e| format!("Could not open the printer share {share}: {e}"))?;
    file.write_all(data)
        .map_err(|e| format!("Could not send the receipt to {share}: {e}"))?;
    file.flush().map_err(|e| e.to_string())
}

#[cfg(unix)]
fn print_to_spooler(printer: &str, data: &[u8]) -> Result<(), String> {
    use std::process::{Command, Stdio};

    let mut command = Command::new("lp");
    if !printer.is_empty() {
        command.arg("-d").arg(printer);
    }
    // -o raw stops CUPS filtering the ESC/POS control codes as if they were text.
    let mut child = command
        .arg("-o")
        .arg("raw")
        .stdin(Stdio::piped())
        .spawn()
        .map_err(|e| format!("Could not start lp: {e}"))?;
    child
        .stdin
        .as_mut()
        .ok_or("lp did not accept input")?
        .write_all(data)
        .map_err(|e| format!("Could not send the receipt to lp: {e}"))?;
    let status = child.wait().map_err(|e| e.to_string())?;
    if status.success() {
        Ok(())
    } else {
        Err(format!("lp rejected the receipt (exit {status})"))
    }
}

#[cfg(not(unix))]
fn print_to_spooler(printer: &str, _data: &[u8]) -> Result<(), String> {
    // Windows has no raw-print CLI worth relying on. Rather than bind winspool,
    // the setting is expected to name a shared printer or a tcp:// address, and
    // this branch explains that instead of failing with something cryptic.
    Err(format!(
        "Cannot print to '{printer}' directly on this platform. Share the printer \
         and set the target to \\\\localhost\\<share name>, or use tcp://<ip>:9100 \
         for a network printer."
    ))
}

/// Printer names to offer in the settings dropdown. Best effort: an empty list
/// just means the cashier types the target in by hand.
#[tauri::command]
pub fn list_printers() -> Vec<String> {
    #[cfg(unix)]
    {
        use std::process::Command;
        let output = match Command::new("lpstat").arg("-a").output() {
            Ok(output) => output,
            Err(_) => return Vec::new(),
        };
        String::from_utf8_lossy(&output.stdout)
            .lines()
            .filter_map(|line| line.split_whitespace().next())
            .map(str::to_string)
            .collect()
    }
    #[cfg(not(unix))]
    {
        use std::process::Command;
        let output = match Command::new("powershell")
            .args(["-NoProfile", "-Command", "Get-Printer | Select-Object -ExpandProperty Name"])
            .output()
        {
            Ok(output) => output,
            Err(_) => return Vec::new(),
        };
        String::from_utf8_lossy(&output.stdout)
            .lines()
            .map(str::trim)
            .filter(|line| !line.is_empty())
            .map(str::to_string)
            .collect()
    }
}

/// `ToSocketAddrs` yields an iterator; this picks the first address and keeps the
/// call sites free of the error juggling that goes with it.
trait ToSocketAddrsCompat {
    fn to_socket_addrs_compat(&self) -> Option<std::net::SocketAddr>;
}

impl ToSocketAddrsCompat for str {
    fn to_socket_addrs_compat(&self) -> Option<std::net::SocketAddr> {
        use std::net::ToSocketAddrs;
        // A bare host means the standard RAW printing port.
        let with_port = if self.contains(':') {
            self.to_string()
        } else {
            format!("{self}:9100")
        };
        with_port.to_socket_addrs().ok()?.next()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn an_empty_job_is_rejected_before_any_connection_is_attempted() {
        let result = print_raw("tcp://192.0.2.1:9100".to_string(), Vec::new());
        assert_eq!(result, Err("Nothing to print".to_string()));
    }

    #[test]
    fn a_bare_host_defaults_to_the_raw_printing_port() {
        let resolved = "127.0.0.1".to_socket_addrs_compat().unwrap();
        assert_eq!(resolved.port(), 9100);
    }

    #[test]
    fn an_explicit_port_is_respected() {
        let resolved = "127.0.0.1:9101".to_socket_addrs_compat().unwrap();
        assert_eq!(resolved.port(), 9101);
    }
}
