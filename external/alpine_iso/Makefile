ALPINE_LINUX_TAG := edge
ALPINE_LINUX_REPO_EDGE := http://dl-cdn.alpinelinux.org/alpine/edge/main
ALPINE_LINUX_REPO_EDGE_COMMUNITY := http://dl-cdn.alpinelinux.org/alpine/edge/community
ALPINE_LINUX_REPO_TESTING := http://dl-cdn.alpinelinux.org/alpine/edge/testing
DOCKER_IMAGE_NAME := alpinelinux-iso-builder
RSA_KEY_PASSPHRASE ?= pass
CURRENT_DIR = $(dir $(abspath $(lastword $(MAKEFILE_LIST))))
OUTPUT_DIR := $(CURRENT_DIR)/iso

all: build-docker

setup-docker-image:
	@echo "Building Docker image..."
	@echo
	@sudo docker build -t $(DOCKER_IMAGE_NAME):latest \
		--build-arg USER_ID=$(shell id -u) \
		--build-arg RSA_KEY_PASSPHRASE="$(RSA_KEY_PASSPHRASE)" \
		-f $(CURRENT_DIR)/Dockerfile $(CURRENT_DIR)

build-docker: setup-docker-image
	@echo "Starting build environment using Docker..."
	@echo
	mkdir -p $(OUTPUT_DIR)
	sudo docker run --rm --workdir /workdir -v $(CURRENT_DIR):/workdir \
		-v $(OUTPUT_DIR):/output $(DOCKER_IMAGE_NAME):latest \
		/bin/ash -c "make build-local OUTPUT_DIR=/output"
	mv $(OUTPUT_DIR)/alpine-x86_64.iso $(OUTPUT_DIR)/system.iso

build-local:
	#@echo "Building extra packages..."
	#@echo
	#mkdir -p $(CURRENT_DIR)/packages
	#cd $(CURRENT_DIR)/extras/sample-package; abuild -r -P $(CURRENT_DIR)/packages
	@echo "Building ISO..."
	@echo "Output directory:" $(OUTPUT_DIR)
	@echo
	mkdir -p $(OUTPUT_DIR)
	## add '--repository $(CURRENT_DIR)/packages/extras' for local repository.
	sh mkimage.sh \
		--outdir $(OUTPUT_DIR) \
		--tag $(ALPINE_LINUX_TAG) \
		--repository "$(ALPINE_LINUX_REPO_EDGE)" \
		--repository "$(ALPINE_LINUX_REPO_EDGE_COMMUNITY)" \
		--repository "$(ALPINE_LINUX_REPO_TESTING)"
