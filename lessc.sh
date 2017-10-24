#!/bin/bash

set -e
set -x
watch-lessc -d resources/styles -i resources/styles/screen.less -o resources/public/css/screen.css
