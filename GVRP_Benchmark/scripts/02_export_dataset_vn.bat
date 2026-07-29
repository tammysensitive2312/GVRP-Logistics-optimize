@echo off
REM =====================================================================
REM 02_export_dataset_vn.bat
REM Dong bang dataset that ra file de tai lap duoc thi nghiem.
REM
REM CAN: 00_env.bat (copy tu 00_env.example.bat va dien gia tri that)
REM      _list_base_tables.sql (di kem repo)
REM CHAY: tu chinh thu muc GVRP_Benchmark\scripts\
REM
REM CHI DUMP BASE TABLE -- co y bo qua VIEW va PROCEDURE:
REM   1) Khong co code Java nao goi toi chung (khao sat 2026-07-27).
REM   2) Cac view trong DB hien dang HONG (loi 1356) nen mysqldump cua
REM      ca database se that bai. Xem RUNBOOK.md muc "View hong".
REM
REM BAO MAT: script nay KHONG BAO GIO in ra %MYSQL_RUN% hay %DUMP_RUN%
REM vi chung chua mat khau. Chi in ban da che.
REM
REM Ghi chu: moi text trong lenh ECHO deu khong dau, de cmd.exe khong
REM hien mojibake o codepage mac dinh.
REM =====================================================================

setlocal enabledelayedexpansion

if not exist "00_env.bat" (
    echo *** THIEU 00_env.bat
    echo     Chay:  copy 00_env.example.bat 00_env.bat
    echo     Roi mo ra dien MODE / DB / DBUSER / DBPASS / CONTAINER.
    exit /b 1
)
if not exist "_list_base_tables.sql" (
    echo *** THIEU _list_base_tables.sql -- file nay di kem repo.
    exit /b 1
)
call 00_env.bat

set OUT=..\datasets\vn6183
set TBLFILE=%TEMP%\gvrp_base_tables.txt
if not exist "%OUT%" mkdir "%OUT%"

echo.
echo === CAU HINH ========================================================
echo   MODE      : %MODE%
echo   DB        : %DB%
echo   DBUSER    : %DBUSER%
if /i "%MODE%"=="docker" echo   CONTAINER : %CONTAINER%
echo   Mat khau  : (khong in ra)
echo =====================================================================
echo.
echo === LOAI TRU CO CHU Y ===============================================
echo   users     : chua hash mat khau
echo   branches  : chua branch_webhook_url (Slack webhook = SECRET)
echo               duoc sinh lai o buoc 4 voi webhook dat NULL
echo   VIEW      : khong code Java nao dung, va dang hong (loi 1356)
echo   PROCEDURE : khong code Java nao dung
echo   solutions / routes / route_stops / unassigned_orders
echo   optimization_jobs
echo             : day la KET QUA, khong phai dau vao. Chi dump SCHEMA
echo               cua chung de app ghi ket qua duoc, KHONG dump du lieu.
echo =====================================================================
echo.

echo [0/5] Kiem tra ket noi...
%MYSQL_RUN% -N -B -e "SELECT 1;" %DB% > nul
if errorlevel 1 goto fail_conn

echo [1/5] Liet ke BASE TABLE (bo qua view)...
%MYSQL_RUN% -N -B %DB% < _list_base_tables.sql > "%TBLFILE%"
if errorlevel 1 goto fail_list

set "TABLES="
for /f "usebackq tokens=* delims=" %%T in ("%TBLFILE%") do (
    if not "%%T"=="" set "TABLES=!TABLES! %%T"
)
if not defined TABLES goto fail_list
echo      !TABLES!
echo.

echo [2/5] Export schema cua cac BASE TABLE...
%DUMP_RUN% --no-data --skip-add-drop-table --triggers ^
  %DB% !TABLES! > "%OUT%\schema.sql"
if errorlevel 1 goto fail

echo [3/5] Export du lieu dau vao...
%DUMP_RUN% --no-create-info --complete-insert --single-transaction ^
  --skip-add-locks --skip-disable-keys ^
  %DB% depots fleets vehicle_types vehicles orders > "%OUT%\data.sql"
if errorlevel 1 goto fail

echo [4/5] Sinh bang branches DA SCRUB (webhook thanh NULL)...
%MYSQL_RUN% -N -B -e "SELECT CONCAT('INSERT INTO branches (id, name, branch_webhook_url) VALUES (', id, ', ', QUOTE(name), ', NULL);') FROM branches;" ^
  %DB% > "%OUT%\branches_scrubbed.sql"
if errorlevel 1 goto fail

echo [5/5] Sinh checksum SHA256...
certutil -hashfile "%OUT%\schema.sql"            SHA256 > "%OUT%\schema.sql.sha256"
certutil -hashfile "%OUT%\data.sql"              SHA256 > "%OUT%\data.sql.sha256"
certutil -hashfile "%OUT%\branches_scrubbed.sql" SHA256 > "%OUT%\branches_scrubbed.sql.sha256"

echo.
echo === XONG. KIEM TRA THU CONG TRUOC KHI COMMIT: =======================
echo   1) Mo branches_scrubbed.sql, xac nhan moi dong ket thuc bang NULL);
echo   2) findstr /I "hooks.slack password secret" "%OUT%\*.sql"
echo      ket qua phai la KHONG TIM THAY
echo   3) findstr /C:"route_sequence" "%OUT%\schema.sql"
echo      neu KHONG tim thay: bang sequence chua ton tai trong DB.
echo      Hibernate se tu tao luc khoi dong -- xem RUNBOOK.md
echo   4) dir "%OUT%"
echo      neu data.sql lon hon 50MB, xem ..\.gitignore ve Git LFS
echo   5) Chay 01_profile_dataset.sql, dien so lieu vao DATASET.md
echo      (thay het cac cho ghi TODO)
echo =====================================================================
endlocal
exit /b 0

:fail_conn
echo.
echo *** LOI: khong ket noi duoc MySQL.
if /i "%MODE%"=="docker" (
    echo     - Container "%CONTAINER%" dang chay? Kiem tra: docker ps
    echo     - Container co client mysql khong? Thu:
    echo       docker exec -i %CONTAINER% mysql --version
) else (
    echo     - mysql/mysqldump co trong PATH khong?
)
echo     - Ten DB "%DB%", user "%DBUSER%", mat khau trong 00_env.bat dung chua?
endlocal
exit /b 1

:fail_list
echo.
echo *** LOI: khong liet ke duoc BASE TABLE.
echo     Chay tay de xem thong bao that (tu dien mat khau vao cho ****):
if /i "%MODE%"=="docker" (
    echo       docker exec -i -e MYSQL_PWD=**** %CONTAINER% mysql -u %DBUSER% -N -B %DB% ^< _list_base_tables.sql
) else (
    echo       mysql -u %DBUSER% -p**** -N -B %DB% ^< _list_base_tables.sql
)
endlocal
exit /b 1

:fail
echo.
echo *** LOI o mot buoc export. Xem thong bao phia tren.
echo     Luu y: file da ghi mot phan co the KHONG hop le -- dung dung no.
endlocal
exit /b 1
