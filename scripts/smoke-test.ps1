# Smoke test for the running stack. Run after: docker compose up -d --build
# Usage:  pwsh -File scripts/smoke-test.ps1        (from the repository root)
#
# It walks the whole asynchronous chain:
#   gateway -> product-service -> Kafka -> search-service
#   gateway -> cart-service -> order-service -> Kafka -> product-service -> order-service
# and finally checks the failure path (not enough stock -> REJECTED).

$ErrorActionPreference = 'Continue'
$base = 'http://localhost:8080/api'
$failures = 0

function Check($label, $condition, $detail) {
    if ($condition) { "PASS  $label  $detail" }
    else { "FAIL  $label  $detail"; $script:failures++ }
}

# 0. wait for the gateway to reach a registered product-service
$ready = $false
for ($i = 0; $i -lt 24; $i++) {
    try { if ((Invoke-WebRequest "$base/products" -UseBasicParsing -TimeoutSec 6).StatusCode -eq 200) { $ready = $true; break } }
    catch { Start-Sleep -Seconds 5 }
}
Check 'gateway reaches product-service' $ready ''
if (-not $ready) { exit 1 }

# 1. create a product -> product-events -> Elasticsearch index
$body = @{ name = 'Mechanical Keyboard'; description = 'Hot-swap ISO keyboard with brown switches'
    category = 'input'; price = 89.90; stock = 20 } | ConvertTo-Json
$p = Invoke-RestMethod "$base/products" -Method Post -ContentType 'application/json' -Body $body
Check 'product created' ($p.id -ne $null) "id=$($p.id) stock=$($p.stock)"

# 2. read it back (second read comes from the Redis cache)
$g = Invoke-RestMethod "$base/products/$($p.id)"
Check 'product read' ($g.name -eq 'Mechanical Keyboard') "name=$($g.name)"

# 3. search must see it (Kafka -> search-service -> Elasticsearch)
$hit = $false
for ($i = 0; $i -lt 12; $i++) {
    try {
        $s = Invoke-RestMethod "$base/search?q=keyboard&category=input&minPrice=50&maxPrice=100&page=0&size=5"
        if ($s.total -ge 1) { $hit = $true; break }
    } catch { }
    Start-Sleep -Seconds 5
}
Check 'search index in sync' $hit "total=$($s.total)"

# 4. cart: add, change quantity, checkout
$c1 = Invoke-RestMethod "$base/carts/alice/items" -Method Post -ContentType 'application/json' -Body (@{productId = $p.id; quantity = 2 } | ConvertTo-Json)
$c2 = Invoke-RestMethod "$base/carts/alice/items/$($p.id)" -Method Put -ContentType 'application/json' -Body (@{quantity = 3 } | ConvertTo-Json)
Check 'cart add + quantity change' ($c2.items[0].quantity -eq 3) "quantity=$($c2.items[0].quantity) ttl=$($c2.ttl)"

$co = Invoke-RestMethod "$base/carts/alice/checkout" -Method Post
Check 'checkout created an order' ($co.orderId -ne $null) "id=$($co.orderId) status=$($co.status) total=$($co.totalAmount)"

# 5. the order must move PENDING -> CONFIRMED through Kafka
$status = 'PENDING'
for ($i = 0; $i -lt 12; $i++) {
    $o = Invoke-RestMethod "$base/orders/$($co.orderId)"
    $status = $o.status
    if ($status -ne 'PENDING') { break }
    Start-Sleep -Seconds 5
}
Check 'order CONFIRMED asynchronously' ($status -eq 'CONFIRMED') "status=$status"

# 6. cart cleared, stock reduced by 3
$cart = Invoke-RestMethod "$base/carts/alice"
$stock = (Invoke-RestMethod "$base/products/$($p.id)").stock
Check 'cart cleared after accepted checkout' ($cart.items.Count -eq 0) "items=$($cart.items.Count)"
Check 'stock reserved exactly once' ($stock -eq 17) "stock=$stock (20 - 3)"

# 7. failure path: not enough stock -> REJECTED, stock untouched
$scarce = Invoke-RestMethod "$base/products" -Method Post -ContentType 'application/json' -Body (@{name = 'Scarce Cap'; description = 'limited edition'; category = 'apparel'; price = 19.5; stock = 1 } | ConvertTo-Json)
Invoke-RestMethod "$base/carts/carol/items" -Method Post -ContentType 'application/json' -Body (@{productId = $scarce.id; quantity = 5 } | ConvertTo-Json) | Out-Null
$co2 = Invoke-RestMethod "$base/carts/carol/checkout" -Method Post
$status2 = 'PENDING'
for ($i = 0; $i -lt 10; $i++) {
    $o2 = Invoke-RestMethod "$base/orders/$($co2.orderId)"
    $status2 = $o2.status
    if ($status2 -ne 'PENDING') { break }
    Start-Sleep -Seconds 3
}
Check 'order REJECTED when stock too low' ($status2 -eq 'REJECTED') "status=$status2 reason=$($o2.rejectionReason)"
Check 'stock untouched after rejection' ((Invoke-RestMethod "$base/products/$($scarce.id)").stock -eq 1) ''

""
if ($failures -eq 0) { "ALL CHECKS PASSED" } else { "$failures CHECK(S) FAILED" }
