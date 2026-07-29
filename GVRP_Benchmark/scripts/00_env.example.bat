@echo off
REM =====================================================================
REM 00_env.example.bat  --  MAU CAU HINH
REM
REM COPY file nay thanh 00_env.bat roi dien gia tri that.
REM 00_env.bat DA DUOC GITIGNORE vi co the chua mat khau.
REM =====================================================================

REM --- Chon MODE: docker | native ---------------------------------------
set MODE=docker

REM --- Thong tin ket noi ------------------------------------------------
set DB=gvrp_db
set DBUSER=root
set DBPASS=doi_mat_khau_o_day

REM --- Chi dung khi MODE=docker -----------------------------------------
REM Lay ten/ID: docker ps --format "{{.Names}}\t{{.ID}}"
REM 12 ky tu dau cua ID la du.
set CONTAINER=2f67f29b9d1c


REM =====================================================================
REM Tu day tro xuong khong can sua.
REM =====================================================================
if /i "%MODE%"=="docker" (
    set "MYSQL_RUN=docker exec -i -e MYSQL_PWD=%DBPASS% %CONTAINER% mysql -u %DBUSER%"
    set "DUMP_RUN=docker exec -i -e MYSQL_PWD=%DBPASS% %CONTAINER% mysqldump -u %DBUSER%"
) else (
    set "MYSQL_PWD=%DBPASS%"
    set "MYSQL_RUN=mysql -u %DBUSER%"
    set "DUMP_RUN=mysqldump -u %DBUSER%"
)

REM Ghi chu ve bao mat: mat khau nam trong bien moi truong cua tien trinh
REM con, khong nam trong argv cua mysql, nen khong hien o "docker top".
REM Nhung no VAN nam trong file nay -- do la ly do 00_env.bat bi gitignore.
