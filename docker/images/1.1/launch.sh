#!/bin/bash

xvfb-run --listen-tcp --server-num 44 --auth-file /tmp/xvfb.auth -s "-ac -screen 0 1920x1080x24" java -jar cerberus-executor-1.1.0.jar