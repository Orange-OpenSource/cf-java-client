/*
 * Copyright 2013-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.cloudfoundry.client.lib.domain;

public class CloudServiceLastOperation {

    private String description;

    private OperationState state;

    private OperationType type;

    public CloudServiceLastOperation() {
    }

    public CloudServiceLastOperation(
            final String description,
            final OperationState operationState) {
        setDescription(description);
        this.state = operationState;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getState() {
        switch (state) {
            case IN_PROGRESS:
                return "in progress";
            case SUCCEEDED:
                return "succeeded";
            case FAILED:
                return "failed";
        }
        ;
        assert (false);
        return "internal error";
    }

    public void setState(String state) {
        switch (state) {
            case "in progress":
                this.state = OperationState.IN_PROGRESS;
                break;
            case "succeeded":
                this.state = OperationState.SUCCEEDED;
                break;
            case "failed":
                this.state = OperationState.FAILED;
                break;
            default:
                assert (false);
                break;
        }
    }

    public OperationType getType() {
        return type;
    }

    public void setType(OperationType type) {
        this.type = type;
    }
}
