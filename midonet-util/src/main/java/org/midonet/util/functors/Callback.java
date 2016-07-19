/*
 * Copyright 2014 Midokura SARL
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.midonet.util.functors;

/**
 * Callback type interface
 */
public interface Callback<T, E extends Exception> {

    public void onSuccess(T data);

    public void onTimeout();

    public void onError(E e);

    public interface MultiResult<T> extends Iterable<Result<T>> {

        public boolean hasTimeouts();

        public boolean hasExceptions();
    }

    public interface Result<T> {

        public String operation();

        public T getData();

        public boolean timeout();

        public Exception exception();
    }
}