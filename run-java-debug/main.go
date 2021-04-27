package main

import (
    "fmt"
    "os"
    "os/exec"
    "time"
)

func main() {
    fmt.Printf("Starting up JAVA project, arguments: %#v\n", os.Args)

    err := exec.Command("/usr/local/openjdk-15/bin/java", os.Args[1:]...).Run()
    fmt.Printf("Java application exit: err=%#v\nerrStr=%q\n", err, err)

    fmt.Println("Sleeping for an hour to allow you to collect the output: /oom-dump.hprof")
    time.Sleep(time.Hour)
}