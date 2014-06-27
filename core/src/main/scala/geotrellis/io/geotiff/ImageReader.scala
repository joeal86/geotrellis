/*
 * Copyright (c) 2014 Azavea.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package geotrellis.io.geotiff

import monocle.syntax._
import monocle.Macro._

import java.nio.ByteBuffer

import geotrellis.io.geotiff.ImageDirectoryLenses._
import geotrellis.io.geotiff.utils.ByteBufferUtils._
import geotrellis.io.geotiff.decompression.HuffmanDecompression._
import geotrellis.io.geotiff.decompression.PackBitsDecompression._
import geotrellis.io.geotiff.decompression.LZWDecompression._

case class ImageReader(byteBuffer: ByteBuffer) {

  val Uncompressed = 1
  val HuffmanCoded = 2
  val LZWCoded = 5
  val JpegCoded = 6
  val PackBitsCoded = 32773

  def read(directory: ImageDirectory): ImageDirectory = {
    val matrix = readMatrix(directory)

    val uncompressedImage = directory |-> compressionLens get match {
      case Uncompressed => matrix.flatten
      case HuffmanCoded => matrix.uncompressHuffman
      case LZWCoded => matrix.uncompressLZW
      case PackBitsCoded => matrix.uncompressPackBits(directory)
    }

    directory |-> imageBytesLens set(uncompressedImage)
  }

  def readMatrix(directory: ImageDirectory): Vector[Vector[Byte]] =
    if (directory.hasStripStorage) readStrips(directory)
    else readTiles(directory)

  def readStrips(directory: ImageDirectory): Vector[Vector[Byte]] = {
    val stripOffsets = directory |-> stripOffsetsLens get
    val stripByteCounts = directory |-> stripByteCountsLens get

    readSections(stripOffsets.get, stripByteCounts.get)
  }

  private def readTiles(directory: ImageDirectory) = {
    val tileOffsets = directory |-> tileOffsetsLens get
    val tileByteCounts = directory |-> tileByteCountsLens get

    readSections(tileOffsets.get, tileByteCounts.get)
  }

  private def readSections(offsets: Vector[Int], byteCounts: Vector[Int]) = {
    val oldOffset = byteBuffer.position

    val matrix = (for (i <- 0 until offsets.size) yield {
      byteBuffer.position(offsets(i))
      byteBuffer.getSignedByteVector(byteCounts(i))
    }).toVector

    byteBuffer.position(oldOffset)

    matrix
  }
}
