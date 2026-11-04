$device = "00016356T000800"
$artifactDir = "C:\Users\sayantan\.gemini\antigravity\brain\382ecc66-6de7-4cbc-bfbe-437be8d43cbb"

Write-Host "=== SettleZero End-to-End Device Verification ===" -ForegroundColor Cyan

# 1. Keep screen on and wake up
Write-Host "[1/8] Waking up and keeping screen on..." -ForegroundColor Yellow
adb -s $device shell svc power stayon true
adb -s $device shell input keyevent KEYCODE_WAKEUP
adb -s $device shell wm dismiss-keyguard
Start-Sleep -Milliseconds 600

# 2. Reset app data for fresh start
Write-Host "[2/8] Resetting app state for clean run..." -ForegroundColor Yellow
adb -s $device shell pm clear app.settlezero
Start-Sleep -Milliseconds 600

# 3. Launch SettleZero
Write-Host "[3/8] Launching SettleZero MainActivity..." -ForegroundColor Yellow
adb -s $device shell am start -n app.settlezero/.MainActivity
Start-Sleep -Milliseconds 1500

# Screenshot 1: Fresh Empty Home
adb -s $device shell screencap -p /sdcard/01_empty_home.png
adb -s $device pull /sdcard/01_empty_home.png "$artifactDir\01_empty_home.png"
Write-Host "   -> Captured 01_empty_home.png" -ForegroundColor Green

# 4. Tap 'Create Group' and fill form
Write-Host "[4/8] Creating new group 'Goa Trip'..." -ForegroundColor Yellow
adb -s $device shell input tap 630 1679
Start-Sleep -Milliseconds 800

# Type group name 'Goa Trip'
adb -s $device shell input text Goa%sTrip
Start-Sleep -Milliseconds 400

# Tap creator name field and type 'Sayantan'
adb -s $device shell input tap 630 890
Start-Sleep -Milliseconds 400
adb -s $device shell input text Sayantan
Start-Sleep -Milliseconds 400

# Tap 'Create'
adb -s $device shell input tap 900 1358
Start-Sleep -Milliseconds 1500

# Screenshot 2: Group Created
adb -s $device shell screencap -p /sdcard/02_group_created.png
adb -s $device pull /sdcard/02_group_created.png "$artifactDir\02_group_created.png"
Write-Host "   -> Captured 02_group_created.png" -ForegroundColor Green

# 5. Add Member 'Bob'
Write-Host "[5/8] Adding member 'Bob'..." -ForegroundColor Yellow
adb -s $device shell input tap 1104 906
Start-Sleep -Milliseconds 800
adb -s $device shell input tap 630 1409
Start-Sleep -Milliseconds 400
adb -s $device shell input text Bob
Start-Sleep -Milliseconds 400
adb -s $device shell input tap 926 1649
Start-Sleep -Milliseconds 1200

# Add Member 'Alice'
Write-Host "   Adding member 'Alice'..." -ForegroundColor Yellow
adb -s $device shell input tap 1104 906
Start-Sleep -Milliseconds 800
adb -s $device shell input tap 630 1409
Start-Sleep -Milliseconds 400
adb -s $device shell input text Alice
Start-Sleep -Milliseconds 400
adb -s $device shell input tap 926 1649
Start-Sleep -Milliseconds 1200

# Screenshot 3: Members Added
adb -s $device shell screencap -p /sdcard/03_members_added.png
adb -s $device pull /sdcard/03_members_added.png "$artifactDir\03_members_added.png"
Write-Host "   -> Captured 03_members_added.png" -ForegroundColor Green

# 6. Add Expense: Beach Shack Dinner (₹3000)
Write-Host "[6/8] Adding Expense 'Beach Shack Dinner' ₹3000..." -ForegroundColor Yellow
adb -s $device shell input tap 273 906
Start-Sleep -Milliseconds 1200

# Description
adb -s $device shell input tap 630 498
Start-Sleep -Milliseconds 400
adb -s $device shell input text Beach%sShack%sDinner
Start-Sleep -Milliseconds 400

# Amount
adb -s $device shell input tap 630 738
Start-Sleep -Milliseconds 400
adb -s $device shell input text 3000
Start-Sleep -Milliseconds 400

# Close keyboard
adb -s $device shell input keyevent 4
Start-Sleep -Milliseconds 600

# Tap Save Expense
adb -s $device shell input tap 630 2677
Start-Sleep -Milliseconds 1500

# Screenshot 4: Expense Added & Balance Simplified
adb -s $device shell screencap -p /sdcard/04_expense_added.png
adb -s $device pull /sdcard/04_expense_added.png "$artifactDir\04_expense_added.png"
Write-Host "   -> Captured 04_expense_added.png" -ForegroundColor Green

# 7. Open More -> Activity History & Export
Write-Host "[7/8] Opening Activity History & Export screen..." -ForegroundColor Yellow
adb -s $device shell input tap 1176 258
Start-Sleep -Milliseconds 800
adb -s $device shell input tap 971 426
Start-Sleep -Milliseconds 1500

# Screenshot 5: Activity History
adb -s $device shell screencap -p /sdcard/05_activity_history.png
adb -s $device pull /sdcard/05_activity_history.png "$artifactDir\05_activity_history.png"
Write-Host "   -> Captured 05_activity_history.png" -ForegroundColor Green

# 8. Tap 'Export JSON'
Write-Host "[8/8] Tapping 'Export JSON'..." -ForegroundColor Yellow
adb -s $device shell input tap 1176 258
Start-Sleep -Milliseconds 1500

# Screenshot 6: Export JSON Dialog
adb -s $device shell screencap -p /sdcard/06_export_json_dialog.png
adb -s $device pull /sdcard/06_export_json_dialog.png "$artifactDir\06_export_json_dialog.png"
Write-Host "   -> Captured 06_export_json_dialog.png" -ForegroundColor Green

Write-Host "`nAll end-to-end device tests completed successfully!" -ForegroundColor Green

