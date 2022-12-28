/*
 * Copyright (C) 2021 jsonwebtoken.io
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
package io.jsonwebtoken.impl.security;

import io.jsonwebtoken.impl.lang.CheckedFunction;
import io.jsonwebtoken.security.HashAlgorithm;
import io.jsonwebtoken.security.Request;

import java.security.MessageDigest;

public final class DefaultHashAlgorithm extends CryptoAlgorithm implements HashAlgorithm {

    public static final HashAlgorithm SHA1 = new DefaultHashAlgorithm("sha-1", "SHA-1");
    public static final HashAlgorithm SHA256 = new DefaultHashAlgorithm("sha-256", "SHA-256");

    DefaultHashAlgorithm(String id, String jcaName) {
        super(id, jcaName);
    }

    @Override
    public byte[] hash(final Request<byte[]> request) {
        return execute(request, MessageDigest.class, new CheckedFunction<MessageDigest, byte[]>() {
            @Override
            public byte[] apply(MessageDigest md) {
                return md.digest(request.getPayload());
            }
        });
    }
}
