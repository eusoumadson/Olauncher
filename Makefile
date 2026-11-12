.PHONY: release dry-run test install

PYTHON ?= python3
GRADLEW ?= ./gradlew

dry-run:
	$(PYTHON) app/src/fork/tools/release.py --dry-run

release:
	$(PYTHON) app/src/fork/tools/release.py

test:
	$(PYTHON) -m unittest app/src/fork/tools/test_release.py
	$(GRADLEW) testDebugUnitTest --no-daemon

install:
	$(GRADLEW) installDebug --no-daemon
