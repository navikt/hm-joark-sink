package main

import (
    "fmt"
    "io"
    "os"
    "os/exec"
    "time"
    "net/http"
)

func main() {
    fmt.Printf("Starting up JAVA project, arguments: %#v\n", os.Args)

    out, err := exec.Command("/usr/local/openjdk-15/bin/java", os.Args[1:]...).Output()
    fmt.Printf("Java application exit: err=%#v\nerrStr=%q\ndata:\n%s\n", err, err, out)

    go func() {
        http.HandleFunc("/isready", func(w http.ResponseWriter, r *http.Request) {
            io.WriteString(w, "READY")
        })
        http.HandleFunc("/isalive", func(w http.ResponseWriter, r *http.Request) {
            io.WriteString(w, "ALIVE")
        })
        fmt.Println(http.ListenAndServe(":8080", nil))
    }()

    fmt.Println("Sleeping for an hour to allow you to collect the output: /oom-dump.hprof")
    time.Sleep(time.Hour)
}