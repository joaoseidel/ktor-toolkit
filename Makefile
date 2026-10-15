th = 10

ifeq ($(OS),Windows_NT)
  gradle_cmd := .\gradlew.bat
else
  gradle_cmd := ./gradlew
endif

.DEFAULT_GOAL := help
.PHONY: help setup clean build test coverage lint format api api_check verify docs dist \
	publish_local publish

help: ## List the available targets
	@grep -E '^[a-zA-Z_]+:.*?## .*$$' $(MAKEFILE_LIST) \
		| awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-22s\033[0m %s\n", $$1, $$2}'

setup: ## Make the Gradle wrapper executable
	chmod +x ./gradlew

clean: setup ## Remove all build output
	$(gradle_cmd) clean

build: setup ## Compile, test and lint every module
	$(gradle_cmd) build --parallel --max-workers=$(th)

test: setup ## Run the tests
	$(gradle_cmd) test --parallel --max-workers=$(th)

coverage: setup ## Aggregate coverage; report at report/build/reports/kover/html/index.html
	$(gradle_cmd) :report:koverHtmlReport :report:koverVerify

lint: setup ## Check formatting and style
	$(gradle_cmd) ktlintCheck

format: setup ## Apply ktlint formatting
	$(gradle_cmd) ktlintFormat

api: setup ## Refresh the public API dumps after an intentional change
	$(gradle_cmd) apiDump

api_check: setup ## Verify the public API against the committed dumps
	$(gradle_cmd) apiCheck

verify: lint api_check build coverage ## Run every check a release must pass

docs: setup ## Dokka HTML per module, at <module>/build/dokka/html/index.html
	$(gradle_cmd) dokkaGenerate

# The javadoc jar is registered by the publishing plugin and is not part of `assemble`, so `build`
# alone would leave it out of the release.
dist: build ## Collect every module's jars into build/dist for a release upload
	$(gradle_cmd) dokkaJavadocJar
	mkdir -p build/dist
	cp ktor-toolkit-*/build/libs/*.jar build/dist/
	ls -1 build/dist

publish_local: setup ## Install every module into the local Maven repository
	$(gradle_cmd) publishToMavenLocal -PsignAllPublications=false

publish: setup ## Sign and upload every module to Maven Central
	$(gradle_cmd) publishToMavenCentral
