/*
 * Copyright 2020 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the "License");
 * you may not use this file except in compliance with the License. You may obtain a copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */
package io.netty5.handler.codec.http.websocketx;

import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.Test;

import static io.netty5.buffer.DefaultBufferAllocators.preferredAllocator;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class CloseWebSocketFrameTest {

    @Test
    void testInvalidCode() {
        doTestInvalidCode(() ->
                new CloseWebSocketFrame(preferredAllocator(), WebSocketCloseStatus.ABNORMAL_CLOSURE));

        doTestInvalidCode(() ->
                new CloseWebSocketFrame(preferredAllocator(), WebSocketCloseStatus.ABNORMAL_CLOSURE,
                "invalid code"));

        doTestInvalidCode(() ->
                new CloseWebSocketFrame(preferredAllocator(), 1006, "invalid code"));

        doTestInvalidCode(() ->
                new CloseWebSocketFrame(preferredAllocator(), true, 0, 1006, "invalid code"));
    }

    @Test
    void testValidCode() {
        doTestValidCode(new CloseWebSocketFrame(preferredAllocator(), WebSocketCloseStatus.NORMAL_CLOSURE),
                WebSocketCloseStatus.NORMAL_CLOSURE.code(), WebSocketCloseStatus.NORMAL_CLOSURE.reasonText());

        doTestValidCode(new CloseWebSocketFrame(preferredAllocator(),
                        WebSocketCloseStatus.NORMAL_CLOSURE, "valid code"),
                WebSocketCloseStatus.NORMAL_CLOSURE.code(), "valid code");

        doTestValidCode(new CloseWebSocketFrame(preferredAllocator(), 1000, "valid code"),
                1000, "valid code");

        doTestValidCode(new CloseWebSocketFrame(preferredAllocator(), true, 0, 1000, "valid code"),
                1000, "valid code");
    }

    private static void doTestInvalidCode(ThrowableAssert.ThrowingCallable callable) {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(callable);
    }

    private static void doTestValidCode(CloseWebSocketFrame frame, int expectedCode, String expectedReason) {
        try (frame) {
            assertThat(frame.statusCode()).isEqualTo(expectedCode);
            assertThat(frame.reasonText()).isEqualTo(expectedReason);
        }
    }
}
