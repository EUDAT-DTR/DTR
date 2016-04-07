rem Append to CP
rem Adapted from:
rem $Id: cpappend.bat,v 1.2.4.3 2002/02/13 05:55:21 patrickl Exp $
rem
rem ---------------------

rem Process the first argument
if ""%1"" == """" goto end
set CP=%CP%;%1
shift

rem Process the remaining arguments
:setArgs
if ""%1"" == """" goto doneSetArgs
set CP=%CP% %1
shift
goto setArgs
:doneSetArgs
:end 