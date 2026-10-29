@echo off
chcp 65001 > nul
echo ========================================================
echo   SMTP/POP3 EMAIL CLIENT - JAVA + MYSQL
echo ========================================================
echo [1/2] Bien dich Java...
javac -encoding UTF-8 -cp ".;mysql-connector-j-9.4.0.jar" *.java
if %errorlevel% neq 0 (
    echo [LOI] Bien dich that bai!
    pause
    exit /b %errorlevel%
)
echo [2/2] Khoi dong ung dung (xem log MySQL o day)...
java -Dfile.encoding=UTF-8 -cp ".;mysql-connector-j-9.4.0.jar" MailAppGUI
echo Ung dung da dong.
pause
