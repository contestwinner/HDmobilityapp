#!/usr/bin/env python3
"""Pleos Virtual Console(http://127.0.0.1:8088) 를 스크립트로 조작한다. 시연 영상 녹화용.

사용: python3 vc.py gear D | seat rear_right false | state
사전: adb forward tcp:8088 tcp:8088
"""
import json, sys, threading, time, urllib.request

BASE = "http://127.0.0.1:8088"
CLIENT = "chavis-demo"


def call(method, path):
    req = urllib.request.Request(BASE + path, method=method, data=b"" if method != "GET" else None)
    with urllib.request.urlopen(req, timeout=5) as r:
        return r.read().decode()


def heartbeat(stop):
    while not stop.is_set():
        call("POST", f"/browser/session?client={CLIENT}")
        time.sleep(1)


def run(args):
    if args[0] == "gear":
        return call("PUT", f"/vehicle/gear?gear={args[1]}")
    if args[0] == "seat":
        return call("PUT", f"/vehicle/seat?kind=occupied&area={args[1]}&value={args[2]}")
    return json.dumps(json.loads(call("GET", "/vehicle/state")).get("gear"), ensure_ascii=False)


if __name__ == "__main__":
    stop = threading.Event()
    threading.Thread(target=heartbeat, args=(stop,), daemon=True).start()
    time.sleep(2.5)
    print(run(sys.argv[1:] or ["state"]))
    time.sleep(1)
    stop.set()
