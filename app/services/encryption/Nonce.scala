package services.encryption

import org.abstractj.kalium.crypto.Random

class Nonce(val raw: Array[Byte]) extends AnyVal

object Nonce {
  private val random = new Random()

  def createNonce(): Nonce = {
    import org.abstractj.kalium.NaCl.Sodium.CRYPTO_SECRETBOX_XSALSA20POLY1305_NONCEBYTES
    new Nonce(
      random.randomBytes(CRYPTO_SECRETBOX_XSALSA20POLY1305_NONCEBYTES)
    )
  }

  def nonceFromByte(data: Array[Byte]): Nonce = {
    import org.abstractj.kalium.NaCl.Sodium.CRYPTO_SECRETBOX_XSALSA20POLY1305_NONCEBYTES
    if (data == null || data.length != CRYPTO_SECRETBOX_XSALSA20POLY1305_NONCEBYTES) {
      throw new IllegalArgumentException(
        "This nonce has an invalid size: " + data.length
      )
    }
    new Nonce(data)
  }
}
