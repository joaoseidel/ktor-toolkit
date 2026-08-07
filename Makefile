# Arguments: use 'th' to define the number of workers in Gradle execution.
## Sample: $ make build th=4
th = 10

# Determine the OS and set the Gradle command accordingly
ifeq ($(OS),Windows_NT)
  gradle_cmd := .\gradlew.bat
else
  gradle_cmd := ./gradlew
endif

.DEFAULT_GOAL := help
.PHONY: help setup clean build test coverage lint format api api_check verify docs dist \
	publish_local publish

# The publishing tasks upload over the network and are not compatible with the configuration cache,
# so every target that reaches Maven Central has to opt out of it.
publish_flags := --no-configuration-cache

help: ## List the available targets
	@grep -E '^[a-zA-Z_]+:.*?## .*$$' $(MAKEFILE_LIST) \
		| awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-22s\033[0m %s\n", $$1, $$2}'

setup: ## Make the Gradle wrapper executable
	chmod +x ./gradlew

clean: setup ## Remove all build output
	$(gradle_cmd) clean

# ─── Build and test ────────────────────────────────────────────────────────

build: setup ## Compile, test and lint every module
	$(gradle_cmd) build --parallel --max-workers=$(th)

test: setup ## Run the tests
	$(gradle_cmd) test --parallel --max-workers=$(th)

coverage: setup ## Aggregate coverage; report at report/build/reports/kover/html/index.html
	$(gradle_cmd) :report:koverHtmlReport :report:koverVerify

# ─── Quality ───────────────────────────────────────────────────────────────

lint: setup ## Check formatting and style
	$(gradle_cmd) ktlintCheck

format: setup ## Apply ktlint formatting
	$(gradle_cmd) ktlintFormat

api: setup ## Refresh the public API dumps after an intentional change
	$(gradle_cmd) apiDump

# Fails when a public signature drifts from the dumps committed under */api.
api_check: setup ## Verify the public API against the committed dumps
	$(gradle_cmd) apiCheck

# Everything a release must pass, in the order a failure is cheapest to read.
verify: lint api_check build coverage ## Run every check a release must pass

# ─── Documentation ─────────────────────────────────────────────────────────

# Also the input for the javadoc jars, so a docs failure surfaces here rather than mid-publish.
docs: setup ## Dokka HTML per module, at <module>/build/dokka/html/index.html
	$(gradle_cmd) dokkaGenerate

# ─── Distribution ──────────────────────────────────────────────────────────

# The javadoc jar is registered by the publishing plugin and is not part of `assemble`, so `build`
# alone would leave it out of the release.
dist: build ## Collect every module's jars into build/dist for a release upload
	$(gradle_cmd) dokkaJavadocJar
	mkdir -p build/dist
	cp ktor-toolkit-*/build/libs/*.jar build/dist/
	ls -1 build/dist

# Unsigned, so this works without a GPG key. Resolves as io.github.joaoseidel:ktor-toolkit-* from
# mavenLocal().
publish_local: setup ## Install every module into the local Maven repository
	$(gradle_cmd) publishToMavenLocal -PsignAllPublications=false $(publish_flags)

# Needs mavenCentralUsername/Password and the signingInMemory* properties — see RELEASING.md.
# Normally invoked by the release workflow, not by hand.
publish: setup ## Sign and upload every module to Maven Central
	$(gradle_cmd) publishToMavenCentral $(publish_flags)
