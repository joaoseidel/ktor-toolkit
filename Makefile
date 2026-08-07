# Arguments: use 'th' to define the number of workers in Gradle execution.
## Sample: $ make build th=4
th = 10

# Determine the OS and set the Gradle command accordingly
ifeq ($(OS),Windows_NT)
  gradle_cmd := .\gradlew.bat
else
  gradle_cmd := ./gradlew
endif

.PHONY: setup clean build test coverage lint format api api_check verify docs dist \
	publish_local publish

# The publishing tasks upload over the network and are not compatible with the configuration cache,
# so every target that reaches Maven Central has to opt out of it.
publish_flags := --no-configuration-cache

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

# Everything a release must pass, in the order a failure is cheapest to read.
verify: lint api_check build coverage

# Dokka HTML per module, at <module>/build/dokka/html/index.html. Also the input for the javadoc
# jars, so a docs failure surfaces here rather than mid-publish.
docs: setup
	$(gradle_cmd) dokkaGenerate

# Collects every module's jars (main, sources, javadoc) into build/dist for a release upload.
# The javadoc jar is registered by the publishing plugin and is not part of `assemble`, so `build`
# alone would leave it out of the release.
dist: build
	$(gradle_cmd) dokkaJavadocJar
	mkdir -p build/dist
	cp ktor-toolkit-*/build/libs/*.jar build/dist/
	ls -1 build/dist

# Unsigned, so this works without a GPG key. Resolves as io.github.joaoseidel:ktor-toolkit-* from
# mavenLocal().
publish_local: setup
	$(gradle_cmd) publishToMavenLocal -PsignAllPublications=false $(publish_flags)

# Signs and uploads to Maven Central. Needs mavenCentralUsername/Password and the signingInMemory*
# properties — see RELEASING.md. Normally invoked by the release workflow, not by hand.
publish: setup
	$(gradle_cmd) publishToMavenCentral $(publish_flags)
