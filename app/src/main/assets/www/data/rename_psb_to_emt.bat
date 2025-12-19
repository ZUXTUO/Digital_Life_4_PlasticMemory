@echo off
setlocal enabledelayedexpansion

rem 遍历当前目录及子目录所有文件
for /r %%f in (*.psb.m_merged.bytes) do (
    set "oldname=%%~nxf"
    set "newname=!oldname:.psb.m_merged.bytes=.emtbytes!"
    echo 正在重命名: "%%f" → "%%~dpf!newname!"
    ren "%%f" "!newname!"
)

echo 完成！
pause
