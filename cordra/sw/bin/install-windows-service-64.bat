@echo off

SET PRG=%~dp0%

SET CP=.

REM Get the full name of the directory where the repository is installed
SET DOHOME=%PRG%..

REM add all the jar files in the lib directory to the classpath
FOR %%i IN ("%DOHOME%\lib\*.*" "%DOHOME%\lib\parsers\*.*" "%DOHOME%\lib\jetty\*.*" "%DOHOME%\lib\joid\*.*") DO CALL "%DOHOME%\bin\cpappend.bat" %%i

%DOHOME%\bin\procrun\amd64\prunsrv //US//RepositoryService --DisplayName="Repository Service" --Description="CNRI Digital Object Repository" --JvmMx=1G --Classpath="%CP%" --StartMode=jvm --StartClass=net.cnri.apps.doserver.Main --StartParams=%~f1 --StopMode=jvm --StopClass=net.cnri.apps.doserver.Main --StopMethod=shutdown