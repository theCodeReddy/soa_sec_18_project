$ErrorActionPreference = 'Stop'
$base = if ($env:MARKETGRID_BASE_URL) { $env:MARKETGRID_BASE_URL } else { 'http://localhost:8080' }
$register = @{ name = 'Smoke Buyer'; email = "smoke-$([guid]::NewGuid())@example.com"; password = 'secret'; tenantId = 'tenant-a' } | ConvertTo-Json
$user = Invoke-RestMethod -Method Post -Uri "$base/api/auth/register" -ContentType 'application/json' -Body $register
$login = @{ email = $user.email; password = 'secret' } | ConvertTo-Json
$token = (Invoke-RestMethod -Method Post -Uri "$base/api/auth/login" -ContentType 'application/json' -Body $login).token
$headers = @{ Authorization = "Bearer $token" }
$v1 = Invoke-RestMethod -Method Post -Uri "$base/api/vendors" -Headers $headers -ContentType 'application/json' -Body (@{ tenantId = 'tenant-a'; name = 'Northwind'; email = 'northwind@example.com' } | ConvertTo-Json)
$v2 = Invoke-RestMethod -Method Post -Uri "$base/api/vendors" -Headers $headers -ContentType 'application/json' -Body (@{ tenantId = 'tenant-a'; name = 'Contoso'; email = 'contoso@example.com' } | ConvertTo-Json)
$p1 = Invoke-RestMethod -Method Post -Uri "$base/api/products" -Headers $headers -ContentType 'application/json' -Body (@{ vendorId = $v1.id; tenantId = 'tenant-a'; name = 'Canvas Tote'; price = 19.99; stock = 4 } | ConvertTo-Json)
$p2 = Invoke-RestMethod -Method Post -Uri "$base/api/products" -Headers $headers -ContentType 'application/json' -Body (@{ vendorId = $v2.id; tenantId = 'tenant-a'; name = 'Steel Bottle'; price = 24.99; stock = 4 } | ConvertTo-Json)
$orderBody = @{ customerId = $user.id; tenantId = 'tenant-a'; items = @(@{ productId = $p1.id; vendorId = $v1.id; quantity = 1; unitPrice = 19.99 }, @{ productId = $p2.id; vendorId = $v2.id; quantity = 2; unitPrice = 24.99 }) } | ConvertTo-Json -Depth 5
$order = Invoke-RestMethod -Method Post -Uri "$base/api/orders" -Headers $headers -ContentType 'application/json' -Body $orderBody
if ($order.status -ne 'CONFIRMED' -or $order.items.Count -ne 2) { throw 'Multi-vendor order assertion failed' }
Invoke-RestMethod -Method Get -Uri "$base/api/orders/$($order.id)" -Headers $headers | ConvertTo-Json -Depth 5
Write-Host "PASS: combined order $($order.id) confirmed for two vendors"
