package io.jsonwebtoken.impl.security

import io.jsonwebtoken.JwtException
import io.jsonwebtoken.impl.lang.Bytes
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.EllipticCurveSignatureAlgorithm
import io.jsonwebtoken.security.InvalidKeyException
import io.jsonwebtoken.security.SignatureAlgorithms
import io.jsonwebtoken.security.SignatureException
import org.junit.Test

import javax.crypto.spec.SecretKeySpec
import java.nio.charset.StandardCharsets
import java.security.*
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.X509EncodedKeySpec

import static org.junit.Assert.*

class DefaultEllipticCurveSignatureAlgorithmTest {

    static Collection<EllipticCurveSignatureAlgorithm> algs() {
        return SignatureAlgorithms.values().findAll({ it instanceof EllipticCurveSignatureAlgorithm })
    }

    @Test
    void testConstructorWithWeakKeyLength() {
        try {
            new DefaultEllipticCurveSignatureAlgorithm(128)
        } catch (IllegalArgumentException iae) {
            String msg = 'orderBitLength must equal 256, 384, or 512.'
            assertEquals msg, iae.getMessage()
        }
    }

    @Test
    void testSignWithoutEcKey() {
        def key = new SecretKeySpec(new byte[1], 'foo')
        def data = "foo".getBytes(StandardCharsets.UTF_8)
        def req = new DefaultSignatureRequest(null, null, data, key)
        algs().each {
            try {
                it.sign(req)
            } catch (InvalidKeyException expected) {
                String msg = "Elliptic Curve signing keys must be ECKeys " +
                        "(implement java.security.interfaces.ECKey). Provided key type: " +
                        "javax.crypto.spec.SecretKeySpec."
                assertEquals msg, expected.getMessage()
            }
        }
    }

    @Test
    void testSignWithPublicKey() {
        ECPublicKey key = TestKeys.ES256.pair.public as ECPublicKey
        def request = new DefaultSignatureRequest(null, null, new byte[1], key)
        try {
            SignatureAlgorithms.ES256.sign(request)
        } catch (InvalidKeyException e) {
            String msg = "Elliptic Curve signing keys must be PrivateKeys (implement ${PrivateKey.class.getName()}). " +
                    "Provided key type: ${key.getClass().getName()}."
            assertEquals msg, e.getMessage()
        }
    }

    @Test
    void testSignWithWeakKey() {
        def gen = KeyPairGenerator.getInstance("EC")
        gen.initialize(192) //too week for any JWA EC algorithm
        def pair = gen.generateKeyPair()

        def request = new DefaultSignatureRequest(null, null, new byte[1], pair.getPrivate())
        algs().each {
            try {
                it.sign(request)
            } catch (InvalidKeyException expected) {
                def keyOrderBitLength = pair.getPublic().getParams().getOrder().bitLength()
                String msg = "The provided Elliptic Curve signing key's size (aka Order bit length) is " +
                        "${Bytes.bitsMsg(keyOrderBitLength)}, but the '${it.getId()}' algorithm requires EC Keys with " +
                        "${Bytes.bitsMsg(it.orderBitLength)} per " +
                        "[RFC 7518, Section 3.4](https://datatracker.ietf.org/doc/html/rfc7518#section-3.4)." as String
                assertEquals msg, expected.getMessage()
            }
        }
    }

    @Test
    void testSignWithInvalidKeyFieldLength() {
        def keypair = SignatureAlgorithms.ES256.generateKeyPair()
        def data = "foo".getBytes(StandardCharsets.UTF_8)
        def req = new DefaultSignatureRequest(null, null, data, keypair.private)
        try {
            SignatureAlgorithms.ES384.sign(req)
        } catch (InvalidKeyException expected) {
            String msg = "The provided Elliptic Curve signing key's size (aka Order bit length) is " +
                    "256 bits (32 bytes), but the 'ES384' algorithm requires EC Keys with " +
                    "384 bits (48 bytes) per " +
                    "[RFC 7518, Section 3.4](https://datatracker.ietf.org/doc/html/rfc7518#section-3.4)."
            assertEquals msg, expected.getMessage()
        }
    }

    @Test
    void testVerifyWithoutEcKey() {
        def key = new SecretKeySpec(new byte[1], 'foo')
        def request = new DefaultVerifySignatureRequest(null, null, new byte[1], key, new byte[1])
        algs().each {
            try {
                it.verify(request)
            } catch (InvalidKeyException e) {
                String msg = "Elliptic Curve verification keys must be ECKeys " +
                        "(implement java.security.interfaces.ECKey). Provided key type: " +
                        "javax.crypto.spec.SecretKeySpec."
                assertEquals msg, e.getMessage()
            }
        }
    }

    @Test
    void testVerifyWithPrivateKey() {
        byte[] data = 'foo'.getBytes(StandardCharsets.UTF_8)
        algs().each {
            KeyPair pair = it.generateKeyPair()
            def key = pair.getPrivate()
            def signRequest = new DefaultSignatureRequest(null, null, data, key)
            byte[] signature = it.sign(signRequest)
            def verifyRequest = new DefaultVerifySignatureRequest(null, null, data, key, signature)
            try {
                it.verify(verifyRequest)
            } catch (InvalidKeyException e) {
                String msg = "Elliptic Curve verification keys must be PublicKeys (implement " +
                        "${PublicKey.class.name}). Provided key type: ${key.class.name}."
                assertEquals msg, e.getMessage()
            }
        }
    }

    @Test
    void testVerifyWithWeakKey() {
        def gen = KeyPairGenerator.getInstance("EC")
        gen.initialize(192) //too week for any JWA EC algorithm
        def pair = gen.generateKeyPair()
        def request = new DefaultVerifySignatureRequest(null, null, new byte[1], pair.getPublic(), new byte[1])
        algs().each {
            try {
                it.verify(request)
            } catch (InvalidKeyException expected) {
                def keyOrderBitLength = pair.getPublic().getParams().getOrder().bitLength()
                String msg = "The provided Elliptic Curve verification key's size (aka Order bit length) is " +
                        "${Bytes.bitsMsg(keyOrderBitLength)}, but the '${it.getId()}' algorithm requires EC Keys with " +
                        "${Bytes.bitsMsg(it.orderBitLength)} per " +
                        "[RFC 7518, Section 3.4](https://datatracker.ietf.org/doc/html/rfc7518#section-3.4)." as String
                assertEquals msg, expected.getMessage()
            }
        }
    }

    @Test
    void invalidDERSignatureToJoseFormatTest() {
        def verify = { signature ->
            try {
                DefaultEllipticCurveSignatureAlgorithm.transcodeDERToConcat(signature, 132)
                fail()
            } catch (JwtException e) {
                assertEquals e.message, 'Invalid ECDSA signature format'
            }
        }
        def signature = new byte[257]
        Randoms.secureRandom().nextBytes(signature)
        //invalid type
        signature[0] = 34
        verify(signature)
        def shortSignature = new byte[7]
        Randoms.secureRandom().nextBytes(shortSignature)
        verify(shortSignature)
        signature[0] = 48
//        signature[1] = 0x81
        signature[1] = -10
        verify(signature)
    }

    @Test
    void edgeCaseSignatureToConcatInvalidSignatureTest() {
        try {
            def signature = Decoders.BASE64.decode("MIGBAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
            DefaultEllipticCurveSignatureAlgorithm.transcodeDERToConcat(signature, 132)
            fail()
        } catch (JwtException e) {
            assertEquals e.message, 'Invalid ECDSA signature format'
        }
    }

    @Test
    void edgeCaseSignatureToConcatInvalidSignatureBranchTest() {
        try {
            def signature = Decoders.BASE64.decode("MIGBAD4AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
            DefaultEllipticCurveSignatureAlgorithm.transcodeDERToConcat(signature, 132)
            fail()
        } catch (JwtException e) {
            assertEquals e.message, 'Invalid ECDSA signature format'
        }
    }

    @Test
    void edgeCaseSignatureToConcatInvalidSignatureBranch2Test() {
        try {
            def signature = Decoders.BASE64.decode("MIGBAj4AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
            DefaultEllipticCurveSignatureAlgorithm.transcodeDERToConcat(signature, 132)
            fail()
        } catch (JwtException e) {
            assertEquals e.message, 'Invalid ECDSA signature format'
        }
    }

    @Test
    void edgeCaseSignatureToConcatLengthTest() {
        try {
            def signature = Decoders.BASE64.decode("MIEAAGg3OVb/ZeX12cYrhK3c07TsMKo7Kc6SiqW++4CAZWCX72DkZPGTdCv2duqlupsnZL53hiG3rfdOLj8drndCU+KHGrn5EotCATdMSLCXJSMMJoHMM/ZPG+QOHHPlOWnAvpC1v4lJb32WxMFNz1VAIWrl9Aa6RPG1GcjCTScKjvEE")
            DefaultEllipticCurveSignatureAlgorithm.transcodeDERToConcat(signature, 132)
            fail()
        } catch (JwtException expected) {

        }
    }

    @Test
    void invalidECDSASignatureFormatTest() {
        try {
            def signature = new byte[257]
            Randoms.secureRandom().nextBytes(signature)
            DefaultEllipticCurveSignatureAlgorithm.transcodeConcatToDER(signature)
            fail()
        } catch (JwtException e) {
            assertEquals 'Invalid ECDSA signature format.', e.message
        }
    }

    @Test
    void edgeCaseSignatureLengthTest() {
        def signature = new byte[1]
        DefaultEllipticCurveSignatureAlgorithm.transcodeConcatToDER(signature)
    }

    @Test
    void testPaddedSignatureToDER() {
        def signature = new byte[32]
        Randoms.secureRandom().nextBytes(signature)
        signature[0] = 0 as byte
        DefaultEllipticCurveSignatureAlgorithm.transcodeConcatToDER(signature) //no exception
    }

    @Test
    void ecdsaSignatureCompatTest() {
        def fact = KeyFactory.getInstance("EC");
        def publicKey = "MIGbMBAGByqGSM49AgEGBSuBBAAjA4GGAAQASisgweVL1tAtIvfmpoqvdXF8sPKTV9YTKNxBwkdkm+/auh4pR8TbaIfsEzcsGUVv61DFNFXb0ozJfurQ59G2XcgAn3vROlSSnpbIvuhKrzL5jwWDTaYa5tVF1Zjwia/5HUhKBkcPuWGXg05nMjWhZfCuEetzMLoGcHmtvabugFrqsAg="
        def pub = fact.generatePublic(new X509EncodedKeySpec(Decoders.BASE64.decode(publicKey)))
        def alg = SignatureAlgorithms.ES512
        def verifier = { token ->
            def signatureStart = token.lastIndexOf('.')
            def withoutSignature = token.substring(0, signatureStart)
            def data = withoutSignature.getBytes("US-ASCII")
            def signature = Decoders.BASE64URL.decode(token.substring(signatureStart + 1))
            assertTrue "Signature do not match that of other implementations", alg.verify(new DefaultVerifySignatureRequest(null, null, data, pub, signature))
        }
        //Test verification for token created using https://github.com/auth0/node-jsonwebtoken/tree/v7.0.1
        verifier("eyJhbGciOiJFUzUxMiIsInR5cCI6IkpXVCJ9.eyJ0ZXN0IjoidGVzdCIsImlhdCI6MTQ2NzA2NTgyN30.Aab4x7HNRzetjgZ88AMGdYV2Ml7kzFbl8Ql2zXvBores7iRqm2nK6810ANpVo5okhHa82MQf2Q_Zn4tFyLDR9z4GAcKFdcAtopxq1h8X58qBWgNOc0Bn40SsgUc8wOX4rFohUCzEtnUREePsvc9EfXjjAH78WD2nq4tn-N94vf14SncQ")
        //Test verification for token created using https://github.com/jwt/ruby-jwt/tree/v1.5.4
        verifier("eyJ0eXAiOiJKV1QiLCJhbGciOiJFUzUxMiJ9.eyJ0ZXN0IjoidGVzdCJ9.AV26tERbSEwcoDGshneZmhokg-tAKUk0uQBoHBohveEd51D5f6EIs6cskkgwtfzs4qAGfx2rYxqQXr7LTXCNquKiAJNkTIKVddbPfped3_TQtmHZTmMNiqmWjiFj7Y9eTPMMRRu26w4gD1a8EQcBF-7UGgeH4L_1CwHJWAXGbtu7uMUn")
    }

    @Test
    void verifySwarmTest() {
        algs().each { alg ->
            def withoutSignature = "eyJhbGciOiJFUzUxMiIsInR5cCI6IkpXVCJ9.eyJ0ZXN0IjoidGVzdCIsImlhdCI6MTQ2NzA2NTgyN30"
            KeyPair keypair = alg.generateKeyPair()
            assertNotNull keypair
            assertTrue keypair.getPublic() instanceof ECPublicKey
            assertTrue keypair.getPrivate() instanceof ECPrivateKey
            def data = withoutSignature.getBytes("US-ASCII")
            def signature = alg.sign(new DefaultSignatureRequest<Key>(null, null, data, keypair.private))
            assertTrue alg.verify(new DefaultVerifySignatureRequest(null, null, data, keypair.public, signature))
        }
    }

    // ===================== Begin imported EllipticCurveSignerTest test cases ==============================

    /*

    @Test
    void testDoSignWithInvalidKeyException() {

        SignatureAlgorithm alg = SignatureAlgorithm.ES256

        KeyPair kp = Keys.keyPairFor(alg)
        PrivateKey privateKey = kp.getPrivate()

        String msg = 'foo'
        final java.security.InvalidKeyException ex = new java.security.InvalidKeyException(msg)

        def signer = new EllipticCurveSigner(alg, privateKey) {
            @Override
            protected byte[] doSign(byte[] data) throws java.security.InvalidKeyException, java.security.SignatureException {
                throw ex
            }
        }

        byte[] bytes = new byte[16]
        SignatureProvider.DEFAULT_SECURE_RANDOM.nextBytes(bytes)

        try {
            signer.sign(bytes)
            fail();
        } catch (io.jsonwebtoken.security.SignatureException se) {
            assertEquals se.message, 'Invalid Elliptic Curve PrivateKey. ' + msg
            assertSame se.cause, ex
        }
    }

    @Test
    void testDoSignWithJoseSignatureFormatException() {

        SignatureAlgorithm alg = SignatureAlgorithm.ES256
        KeyPair kp = EllipticCurveProvider.generateKeyPair(alg)
        PublicKey publicKey = kp.getPublic();
        PrivateKey privateKey = kp.getPrivate();

        String msg = 'foo'
        final JwtException ex = new JwtException(msg)

        def signer = new EllipticCurveSigner(alg, privateKey) {
            @Override
            protected byte[] doSign(byte[] data) throws java.security.InvalidKeyException, java.security.SignatureException, JwtException {
                throw ex
            }
        }

        byte[] bytes = new byte[16]
        SignatureProvider.DEFAULT_SECURE_RANDOM.nextBytes(bytes)

        try {
            signer.sign(bytes)
            fail();
        } catch (io.jsonwebtoken.security.SignatureException se) {
            assertEquals se.message, 'Unable to convert signature to JOSE format. ' + msg
            assertSame se.cause, ex
        }
    }

    @Test
    void testDoSignWithJdkSignatureException() {

        SignatureAlgorithm alg = SignatureAlgorithm.ES256
        KeyPair kp = EllipticCurveProvider.generateKeyPair(alg)
        PublicKey publicKey = kp.getPublic();
        PrivateKey privateKey = kp.getPrivate();

        String msg = 'foo'
        final java.security.SignatureException ex = new java.security.SignatureException(msg)

        def signer = new EllipticCurveSigner(alg, privateKey) {
            @Override
            protected byte[] doSign(byte[] data) throws java.security.InvalidKeyException, java.security.SignatureException {
                throw ex
            }
        }

        byte[] bytes = new byte[16]
        SignatureProvider.DEFAULT_SECURE_RANDOM.nextBytes(bytes)

        try {
            signer.sign(bytes)
            fail();
        } catch (io.jsonwebtoken.security.SignatureException se) {
            assertEquals se.message, 'Unable to calculate signature using Elliptic Curve PrivateKey. ' + msg
            assertSame se.cause, ex
        }
    }

    @Test
    void testDoVerifyWithInvalidKeyException() {

        String msg = 'foo'
        final java.security.InvalidKeyException ex = new java.security.InvalidKeyException(msg)
        def alg = SignatureAlgorithm.ES512
        def keypair = EllipticCurveProvider.generateKeyPair(alg)

        def v = new EllipticCurveSignatureValidator(alg, EllipticCurveProvider.generateKeyPair(alg).public) {
            @Override
            protected boolean doVerify(Signature sig, PublicKey pk, byte[] data, byte[] signature) throws java.security.InvalidKeyException, java.security.SignatureException {
                throw ex;
            }
        }

        byte[] data = new byte[32]
        SignatureProvider.DEFAULT_SECURE_RANDOM.nextBytes(data)

        byte[] signature = new EllipticCurveSigner(alg, keypair.getPrivate()).sign(data)

        try {
            v.isValid(data, signature)
            fail();
        } catch (io.jsonwebtoken.security.SignatureException se) {
            assertEquals se.message, 'Unable to verify Elliptic Curve signature using configured ECPublicKey. ' + msg
            assertSame se.cause, ex
        }
    }

     */

    @Test
    void ecdsaSignatureInteropTest() {
        def fact = KeyFactory.getInstance("EC");
        def publicKey = "MIGbMBAGByqGSM49AgEGBSuBBAAjA4GGAAQASisgweVL1tAtIvfmpoqvdXF8sPKTV9YTKNxBwkdkm+/auh4pR8TbaIfsEzcsGUVv61DFNFXb0ozJfurQ59G2XcgAn3vROlSSnpbIvuhKrzL5jwWDTaYa5tVF1Zjwia/5HUhKBkcPuWGXg05nMjWhZfCuEetzMLoGcHmtvabugFrqsAg="
        def pub = fact.generatePublic(new X509EncodedKeySpec(Decoders.BASE64.decode(publicKey)))
        def alg = SignatureAlgorithms.ES512
        def verifier = { token ->
            def signatureStart = token.lastIndexOf('.')
            def withoutSignature = token.substring(0, signatureStart)
            def signature = token.substring(signatureStart + 1)

            def data = withoutSignature.getBytes(StandardCharsets.US_ASCII)
            def sigBytes = Decoders.BASE64URL.decode(signature)
            def request = new DefaultVerifySignatureRequest(null, null, data, pub, sigBytes)
            assert alg.verify(request), "Signature do not match that of other implementations"
        }
        //Test verification for token created using https://github.com/auth0/node-jsonwebtoken/tree/v7.0.1
        verifier("eyJhbGciOiJFUzUxMiIsInR5cCI6IkpXVCJ9.eyJ0ZXN0IjoidGVzdCIsImlhdCI6MTQ2NzA2NTgyN30.Aab4x7HNRzetjgZ88AMGdYV2Ml7kzFbl8Ql2zXvBores7iRqm2nK6810ANpVo5okhHa82MQf2Q_Zn4tFyLDR9z4GAcKFdcAtopxq1h8X58qBWgNOc0Bn40SsgUc8wOX4rFohUCzEtnUREePsvc9EfXjjAH78WD2nq4tn-N94vf14SncQ")
        //Test verification for token created using https://github.com/jwt/ruby-jwt/tree/v1.5.4
        verifier("eyJ0eXAiOiJKV1QiLCJhbGciOiJFUzUxMiJ9.eyJ0ZXN0IjoidGVzdCJ9.AV26tERbSEwcoDGshneZmhokg-tAKUk0uQBoHBohveEd51D5f6EIs6cskkgwtfzs4qAGfx2rYxqQXr7LTXCNquKiAJNkTIKVddbPfped3_TQtmHZTmMNiqmWjiFj7Y9eTPMMRRu26w4gD1a8EQcBF-7UGgeH4L_1CwHJWAXGbtu7uMUn")
    }

    @Test // asserts guard for JVM security bug CVE-2022-21449:
    void legacySignatureCompatDefaultTest() {
        def withoutSignature = "eyJhbGciOiJFUzUxMiIsInR5cCI6IkpXVCJ9.eyJ0ZXN0IjoidGVzdCIsImlhdCI6MTQ2NzA2NTgyN30"
        def alg = SignatureAlgorithms.ES512
        def keypair = alg.generateKeyPair()
        def signature = Signature.getInstance(alg.jcaName)
        def data = withoutSignature.getBytes(StandardCharsets.US_ASCII)
        signature.initSign(keypair.private)
        signature.update(data)
        def signed = signature.sign()
        def request = new DefaultVerifySignatureRequest(null, null, data, keypair.public, signed)
        try {
            alg.verify(request)
            fail()
        } catch (SignatureException expected) {
            String signedBytesString = Bytes.bytesMsg(signed.length)
            String msg = "Unable to verify Elliptic Curve signature using provided ECPublicKey: Provided " +
                    "signature is $signedBytesString but ES512 signatures must be exactly 1056 bits (132 bytes) " +
                    "per [RFC 7518, Section 3.4 (validation)]" +
                    "(https://datatracker.ietf.org/doc/html/rfc7518#section-3.4)." as String
            assertEquals msg, expected.getMessage()
        }
    }

    @Test
    void legacySignatureCompatWhenEnabledTest() {
        try {
            System.setProperty(DefaultEllipticCurveSignatureAlgorithm.DER_ENCODING_SYS_PROPERTY_NAME, 'true')

            def withoutSignature = "eyJhbGciOiJFUzUxMiIsInR5cCI6IkpXVCJ9.eyJ0ZXN0IjoidGVzdCIsImlhdCI6MTQ2NzA2NTgyN30"
            def alg = SignatureAlgorithms.ES512
            def keypair = alg.generateKeyPair()
            def signature = Signature.getInstance(alg.jcaName)
            def data = withoutSignature.getBytes(StandardCharsets.US_ASCII)
            signature.initSign(keypair.private)
            signature.update(data)
            def signed = signature.sign()
            def request = new DefaultVerifySignatureRequest(null, null, data, keypair.public, signed)
            assertTrue alg.verify(request)
        } finally {
            System.clearProperty(DefaultEllipticCurveSignatureAlgorithm.DER_ENCODING_SYS_PROPERTY_NAME)
        }
    }

    @Test // asserts guard for JVM security bug CVE-2022-21449:
    void testVerifySignatureAllZeros() {
        byte[] forgedSig = new byte[64]
        def withoutSignature = "eyJhbGciOiJFUzUxMiIsInR5cCI6IkpXVCJ9.eyJ0ZXN0IjoidGVzdCIsImlhdCI6MTQ2NzA2NTgyN30"
        def alg = SignatureAlgorithms.ES256
        def keypair = alg.generateKeyPair()
        def data = withoutSignature.getBytes(StandardCharsets.US_ASCII)
        def request = new DefaultVerifySignatureRequest(null, null, data, keypair.public, forgedSig)
        assertFalse alg.verify(request)
    }

    @Test // asserts guard for JVM security bug CVE-2022-21449:
    void testVerifySignatureRZero() {
        byte[] r = new byte[32]
        byte[] s = new byte[32]; Arrays.fill(s, Byte.MAX_VALUE)
        byte[] sig = new byte[r.length + s.length]
        System.arraycopy(r, 0, sig, 0, r.length)
        System.arraycopy(s, 0, sig, r.length, s.length)

        def withoutSignature = "eyJhbGciOiJFUzUxMiIsInR5cCI6IkpXVCJ9.eyJ0ZXN0IjoidGVzdCIsImlhdCI6MTQ2NzA2NTgyN30"
        def alg = SignatureAlgorithms.ES256
        def keypair = alg.generateKeyPair()
        def data = withoutSignature.getBytes(StandardCharsets.US_ASCII)
        def request = new DefaultVerifySignatureRequest(null, null, data, keypair.public, sig)
        assertFalse alg.verify(request)
    }

    @Test // asserts guard for JVM security bug CVE-2022-21449:
    void testVerifySignatureSZero() {
        byte[] r = new byte[32]; Arrays.fill(r, Byte.MAX_VALUE);
        byte[] s = new byte[32]
        byte[] sig = new byte[r.length + s.length]
        System.arraycopy(r, 0, sig, 0, r.length)
        System.arraycopy(s, 0, sig, r.length, s.length)

        def withoutSignature = "eyJhbGciOiJFUzUxMiIsInR5cCI6IkpXVCJ9.eyJ0ZXN0IjoidGVzdCIsImlhdCI6MTQ2NzA2NTgyN30"
        def alg = SignatureAlgorithms.ES256
        def keypair = alg.generateKeyPair()
        def data = withoutSignature.getBytes(StandardCharsets.US_ASCII)
        def request = new DefaultVerifySignatureRequest(null, null, data, keypair.public, sig)
        assertFalse alg.verify(request)
    }

    @Test // asserts guard for JVM security bug CVE-2022-21449:
    void ecdsaInvalidSignatureValuesTest() {
        def withoutSignature = "eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCJ9.eyJ0ZXN0IjoidGVzdCIsImlhdCI6MTQ2NzA2NTgyN30"
        def invalidEncodedSignature = "_____wAAAAD__________7zm-q2nF56E87nKwvxjJVH_____AAAAAP__________vOb6racXnoTzucrC_GMlUQ"
        def alg = SignatureAlgorithms.ES256
        def keypair = alg.generateKeyPair()
        def data = withoutSignature.getBytes(StandardCharsets.US_ASCII)
        def invalidSignature = Decoders.BASE64URL.decode(invalidEncodedSignature)
        def request = new DefaultVerifySignatureRequest(null, null, data, keypair.public, invalidSignature)
        assertFalse("Forged signature must not be considered valid.", alg.verify(request))
    }
}
