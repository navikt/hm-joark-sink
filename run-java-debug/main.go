package main

import (
    "fmt"
    "os"
    "os/exec"
    "time"
)

func main() {
    fmt.Printf("Starting up JAVA project, arguments: %#v\n", os.Args)

    out, err := exec.Command("/usr/local/openjdk-15/bin/java", os.Args[1:]...).Output()
    fmt.Printf("Java application exit: err=%#v\nerrStr=%q\ndata:\n%s\n", err, err, out)

    fmt.Println("Sleeping for an hour to allow you to collect the output: /oom-dump.hprof")
    time.Sleep(time.Hour)
}