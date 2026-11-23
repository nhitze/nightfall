#!/usr/bin/env python3
"""Serve only the local Nightfall browser prototype."""

import argparse
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path


PROTOTYPE_DIRECTORY = Path(__file__).resolve().parent


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--port",
        type=int,
        required=True,
        help="Port allocated for the demo; do not choose an unreserved project port.",
    )
    arguments = parser.parse_args()

    handler = partial(SimpleHTTPRequestHandler, directory=str(PROTOTYPE_DIRECTORY))
    server = ThreadingHTTPServer(("127.0.0.1", arguments.port), handler)
    print(f"Nightfall prototype: http://127.0.0.1:{arguments.port}/NightfallPrototype.html")
    print("Serving the prototype folder only. Press Ctrl-C to stop.")

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
