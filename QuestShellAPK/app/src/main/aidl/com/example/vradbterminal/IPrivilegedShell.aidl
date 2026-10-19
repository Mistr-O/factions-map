package com.example.vradbterminal;

interface IPrivilegedShell {
    String exec(String command);
    int getUid();
    void destroy() = 16777114;
}
