@echo off

SET PRG=%~dp0%

SET CP=.

REM Get the full name of the directory where the repository is installed
SET DOHOME=%PRG%..

REM add all the jar files in the lib directory to the classpath
FOR %%i IN ("%DOHOME%\lib\*.*" "%DOHOME%\lib\parsers\*.*" "%DOHOME%\lib\jetty\*.*" "%DOHOME%\lib\joid\*.*") DO CALL "%DOHOME%\bin\cpappend.bat" %%i

java -cp "%CP%" net.cnri.apps.auditgui.AuditWindow %*