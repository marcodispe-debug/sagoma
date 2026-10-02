@echo off
rem Avvia Sagoma per computer. Usa il Java incluso in Android Studio.
rem Se il programma non e' ancora stato preparato (o si vuole aggiornarlo: avvia-sagoma-pc.bat aggiorna) lo prepara prima.
rem Il programma parte da una sua copia privata: se nel frattempo viene ricompilato, quello aperto
rem continua a funzionare e a salvare.
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
set "JAR=%~dp0desktop\build\compose\jars\Sagoma-windows-x64-1.0.0.jar"
if /i "%1"=="aggiorna" goto prepara
if exist "%JAR%" goto avvia
:prepara
echo Preparo Sagoma per computer... la prima volta ci vuole qualche minuto
call "%~dp0gradlew.bat" :desktop:packageUberJarForCurrentOS
if errorlevel 1 ( pause & exit /b 1 )
:avvia
set "RUN=%LOCALAPPDATA%\Sagoma\programma"
if not exist "%RUN%" mkdir "%RUN%"
rem Copie vecchie non piu' in uso (quelle ancora aperte restano: Windows non le lascia cancellare).
del /q "%RUN%\Sagoma-*.jar" >nul 2>&1
set "COPIA=%RUN%\Sagoma-%RANDOM%%RANDOM%.jar"
copy /y "%JAR%" "%COPIA%" >nul
start "" "%JAVA_HOME%\bin\javaw.exe" "-Dsagoma.assets=%~dp0app\src\pro\assets" -jar "%COPIA%"
