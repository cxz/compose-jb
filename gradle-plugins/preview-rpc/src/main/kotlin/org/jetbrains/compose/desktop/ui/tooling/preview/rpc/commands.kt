/*
 * Copyright 2020-2021 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package org.jetbrains.compose.desktop.ui.tooling.preview.rpc

import java.net.URLDecoder
import java.net.URLEncoder

internal fun RemoteConnection.sendAttach() {
    sendCommand(Command.Type.ATTACH)
}

internal fun RemoteConnection.receiveAttach(fn: () -> Unit) {
    receiveCommand { (type, _) ->
        if (type == Command.Type.ATTACH) {
            fn()
        }
    }
}

internal fun RemoteConnection.sendFrame(frame: RenderedFrame) {
    sendCommand(Command.Type.FRAME, frame.width.toString(), frame.height.toString())
    sendData(frame.bytes)
}

internal fun RemoteConnection.receiveFrame(fn: (RenderedFrame) -> Unit) {
    receiveCommand { (type, args) ->
        if (type == Command.Type.FRAME) {
            receiveData { bytes ->
                val (w, h) = args
                val frame = RenderedFrame(bytes, width = w.toInt(), height = h.toInt())
                fn(frame)
            }
        }
    }
}

fun RemoteConnection.sendConfigFromGradle(
    config: PreviewHostConfig,
    previewClasspath: String,
    previewFqName: String
) {
    sendCommand(Command.Type.PREVIEW_CONFIG, URLEncoder.encode(config.javaExecutable, Charsets.UTF_8))
    sendUtf8StringData(config.hostClasspath)
    sendCommand(Command.Type.PREVIEW_CLASSPATH)
    sendUtf8StringData(previewClasspath)
    sendCommand(Command.Type.PREVIEW_FQ_NAME)
    sendUtf8StringData(previewFqName)
}

internal fun RemoteConnection.receiveConfigFromGradle(
    onPreviewClasspath: (String) -> Unit,
    onPreviewFqName: (String) -> Unit,
    onPreviewHostConfig: (PreviewHostConfig) -> Unit
) {
    receiveCommand { (type, args) ->
        when (type) {
            Command.Type.PREVIEW_CLASSPATH ->
                receiveUtf8StringData { onPreviewClasspath(it) }
            Command.Type.PREVIEW_FQ_NAME ->
                receiveUtf8StringData { onPreviewFqName(it) }
            Command.Type.PREVIEW_CONFIG -> {
                val javaExecutable = URLDecoder.decode(args[0], Charsets.UTF_8)
                receiveUtf8StringData { hostClasspath ->
                    val config = PreviewHostConfig(javaExecutable = javaExecutable, hostClasspath = hostClasspath)
                    onPreviewHostConfig(config)
                }
            }
            else -> {
                // todo
            }
        }
    }
}

internal fun RemoteConnection.sendPreviewRequest(
    previewClasspath: String,
    request: FrameRequest
) {
    sendCommand(Command.Type.PREVIEW_CLASSPATH)
    sendData(previewClasspath.toByteArray(Charsets.UTF_8))
    val (fqName, frameConfig) = request
    val (w, h, scale) = frameConfig
    val args = arrayListOf(fqName, w.toString(), h.toString())
    if (scale != null) {
        val scaleLong = java.lang.Double.doubleToRawLongBits(scale)
        args.add(scaleLong.toString())
    }
    sendCommand(Command.Type.FRAME_REQUEST, *args.toTypedArray())
}

internal fun RemoteConnection.receivePreviewRequest(
    onPreviewClasspath: (String) -> Unit,
    onFrameRequest: (FrameRequest) -> Unit
) {
    receiveCommand { (type, args) ->
        when (type) {
            Command.Type.PREVIEW_CLASSPATH -> {
                receiveUtf8StringData { onPreviewClasspath(it) }
            }
            Command.Type.FRAME_REQUEST -> {
                val fqName = args.getOrNull(0)
                val w = args.getOrNull(1)?.toIntOrNull()
                val h = args.getOrNull(2)?.toIntOrNull()
                val scale = args.getOrNull(3)?.toLongOrNull()?.let { java.lang.Double.longBitsToDouble(it) }
                if (
                    fqName != null && fqName.isNotEmpty()
                        && w != null && w > 0
                        && h != null && h > 0
                ) {
                    onFrameRequest(FrameRequest(fqName, FrameConfig(width = w, height = h, scale = scale)))
                }
            }
            else -> {
                // todo
            }
        }
    }
}