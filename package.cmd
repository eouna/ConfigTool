@echo off
rem ConfigTool packaging entry: build green (portable) folder + single-file exe.
rem Usage: package.cmd [-HttpProxy http://127.0.0.1:10808] [-SkipSmokeTest] [-KeepBuild]
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0package.ps1" %*