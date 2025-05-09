# Arguments: Use the argument 'th' in make command to define the number of threads in Gradle execution.
## Samples:
### $make verify th=4 (build with 4 threads)
th = 10

# Determine the OS and set the Gradle command accordingly
ifeq ($(OS),Windows_NT)
  gradle_cmd := .\gradlew.bat
else
  gradle_cmd := ./gradlew
endif

# Commands to build
setup:
	chmod +x ./gradlew

clean: setup
	$(gradle_cmd) clean

install: clean
	$(gradle_cmd) build --parallel --max-workers=$(th) --no-build-cache

install_without_tests: clean
	$(gradle_cmd) build --parallel --max-workers=$(th) --no-build-cache \
		-x :gel-query-dsl-core:test \
		-x :gel-query-dsl-processor:test \
		-x :gel-query-dsl-runtime:test \
 		-x :acceptance-tests:build \
 		-x :report:build

publish: install_without_tests
	$(gradle_cmd) publishToMavenCentral --no-configuration-cache

# Commands to Linting
lint: setup
	$(gradle_cmd) ktlintCheck

lint_format: setup
	$(gradle_cmd) ktlintFormat

# Test the app
## If you want to run a pipeline, use test_unit, then test_integration, and finally test_report
test_unit: setup
	$(gradle_cmd) test --parallel --max-workers=$(th) --build-cache \
 		:gel-query-dsl-core:test \
 		:gel-query-dsl-processor:test \
 		:gel-query-dsl-runtime:test \
 		:report:build \
		-x :acceptance-tests:test

test_integration: setup
	$(gradle_cmd) test --parallel --max-workers=$(th) --build-cache \
		:acceptance-tests:test \
 		-x :gel-query-dsl-core:test \
 		-x :gel-query-dsl-processor:test \
 		-x :gel-query-dsl-runtime:test \
 		-x :report:build

test_report: setup
	$(gradle_cmd) test --parallel --max-workers=$(th) --build-cache \
		:report:build \
		-x :gel-query-dsl-core:test \
		-x :gel-query-dsl-processor:test \
		-x :gel-query-dsl-runtime:test \
		-x :acceptance-tests:test
