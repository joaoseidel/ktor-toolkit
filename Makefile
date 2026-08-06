# Arguments: use 'th' to define the number of workers in Gradle execution.
## Sample: $ make build th=4
th = 10

# Determine the OS and set the Gradle command accordingly
ifeq ($(OS),Windows_NT)
  gradle_cmd := .\gradlew.bat
else
  gradle_cmd := ./gradlew
endif

.PHONY: setup clean build test coverage lint format api api_check dist publish_local

setup:
	chmod +x ./gradlew

clean: setup
	$(gradle_cmd) clean

build: setup
	$(gradle_cmd) build --parallel --max-workers=$(th)

test: setup
	$(gradle_cmd) test --parallel --max-workers=$(th)

# Aggregated coverage report at report/build/reports/kover/html/index.html
coverage: setup
	$(gradle_cmd) :report:koverHtmlReport :report:koverVerify

lint: setup
	$(gradle_cmd) ktlintCheck

format: setup
	$(gradle_cmd) ktlintFormat

# Refresh the public API dumps after an intentional API change.
api: setup
	$(gradle_cmd) apiDump

# Fails when a public signature drifts from the dumps committed under */api.
api_check: setup
	$(gradle_cmd) apiCheck

# Collects every module's jars (main, sources, javadoc) into build/dist for a release upload.
dist: build
	mkdir -p build/dist
	cp ktor-toolkit-*/build/libs/*.jar build/dist/
	ls -1 build/dist

publish_local: setup
	$(gradle_cmd) publishToMavenLocal
