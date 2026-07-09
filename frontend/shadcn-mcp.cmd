@echo off
rem shadcn MCP sunucusunu her zaman frontend/ icinde baslatir (components.json burada).
rem .mcp.json bunu "cmd /c frontend\shadcn-mcp.cmd" ile cagirir; %~dp0 = bu dosyanin klasoru.
cd /d "%~dp0"
npx -y shadcn@latest mcp
