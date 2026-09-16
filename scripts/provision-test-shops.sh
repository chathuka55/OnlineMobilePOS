#!/usr/bin/env bash
# provision-test-shops.sh
# Usage: API_URL=https://your-api.railway.app PLATFORM_TOKEN=xxx ./scripts/provision-test-shops.sh

set -euo pipefail

API_URL=${API_URL:-"http://localhost:8080"}
TOKEN=${PLATFORM_TOKEN:-""}

if [ -z "$TOKEN" ]; then
  echo "ERROR: PLATFORM_TOKEN is required" >&2
  exit 1
fi

shops=(
  "shop-colombo|Shop 1 Colombo|admin1@shop-colombo.lk|Admin1@secure"
  "shop-kandy|Shop 2 Kandy|admin2@shop-kandy.lk|Admin2@secure"
  "shop-galle|Shop 3 Galle|admin3@shop-galle.lk|Admin3@secure"
  "shop-negombo|Shop 4 Negombo|admin4@shop-negombo.lk|Admin4@secure"
  "shop-jaffna|Shop 5 Jaffna|admin5@shop-jaffna.lk|Admin5@secure"
  "shop-matara|Shop 6 Matara|admin6@shop-matara.lk|Admin6@secure"
  "shop-kurunegala|Shop 7 Kurunegala|admin7@shop-kurunegala.lk|Admin7@secure"
  "shop-badulla|Shop 8 Badulla|admin8@shop-badulla.lk|Admin8@secure"
)

for shop in "${shops[@]}"; do
  IFS='|' read -r slug name email password <<< "$shop"
  
  echo "Provisioning: $name ($slug)…"
  
  response=$(curl -sf -X POST "$API_URL/api/v1/platform/tenants" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"slug\":\"$slug\",\"name\":\"$name\",\"plan\":\"starter\"}")
  
  tenant_id=$(echo "$response" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
  
  curl -sf -X POST "$API_URL/api/v1/platform/tenants/$tenant_id/users" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"$email\",\"fullName\":\"$name Admin\",\"password\":\"$password\",\"role\":\"ADMIN\"}" > /dev/null
  
  echo "  ✓ Created: $name | Login: $email | Pass: $password"
done

echo ""
echo "✅ All 8 test shops provisioned!"
echo "Dashboard URL: $API_URL (replace with your Vercel URL)"
