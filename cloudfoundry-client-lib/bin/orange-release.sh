#!/usr/bin/env bash
#
# Copyright (C) 2015 Orange
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
# http://www.apache.org/licenses/LICENSE-2.0
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

set -ev

if [ "${TRAVIS_TAG}" != ""  ]
then
	export REPO_NAME=$(expr ${TRAVIS_REPO_SLUG} : ".*\/\(.*\)")
	echo "TAG_NAME: $TRAVIS_TAG"
	echo "Extracted Travis repo name: $REPO_NAME"

	JFROG_PROMOTION_URL=http://oss.jfrog.org/api/plugins/build/promote/snapshotsToBintray/cloudfoundry-client-lib/${TRAVIS_BUILD_NUMBER}
	echo "Promotion URL to use: $JFROG_PROMOTION_URL"
	curl --silent -X POST -u ${BINTRAY_USER}:${BINTRAY_PASSWORD} $JFROG_PROMOTION_URL
fi