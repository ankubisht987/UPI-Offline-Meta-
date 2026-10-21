package com.ankush.upi_offline_meta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.ankush.upi_offline_meta.crypto.HybridCryptoService;
import com.ankush.upi_offline_meta.crypto.ServerKeyHolder;
import com.ankush.upi_offline_meta.model.AccountRepository;
import com.ankush.upi_offline_meta.model.MeshPacket;
import com.ankush.upi_offline_meta.model.PaymentInstruction;
import com.ankush.upi_offline_meta.services.DemoService;
import com.ankush.upi_offline_meta.services.IdempotencyService;
import com.ankush.upi_offline_meta.services.MeshSimulatorService;

@SpringBootTest
class UpiOfflineMetaApplicationTests {

	// @Test
	// void contextLoads() {
	// }
	@Autowired
	private HybridCryptoService crypto;
	@Autowired
	private ServerKeyHolder serverKey;
	@Autowired
	private DemoService demo;
	@Autowired
	private AccountRepository accountRepo;

	@Autowired
	private MeshSimulatorService mesh;

	@Autowired
	IdempotencyService idem;

	@Test
	void encryptDecryptRoundTrip() throws Exception {
		PaymentInstruction original = new PaymentInstruction(
				new BigDecimal("123.45"), // amount
				"nonce-1", // nonce
				"abc", // pinHash
				"bob@bank", // receiverVpa
				"ankush@bank", // senderVpa
				System.currentTimeMillis() // signedAt
		);

		String ct = crypto.encrypt(original, serverKey.getPublicKey());
		PaymentInstruction back = crypto.decrypt(ct);

		assertEquals(original.getSenderVpa(), back.getSenderVpa());
		assertEquals(original.getReceiverVpa(), back.getReceiverVpa());
		assertEquals(0, original.getAmount().compareTo(back.getAmount()));
		assertEquals(original.getNonce(), back.getNonce());
	}

	@Test
	void tamperedCiphertextFails() throws Exception {
		PaymentInstruction original = new PaymentInstruction(
				new BigDecimal("50.00"), "nonce-2", "abc",
				"bob@bank", "ankush@bank", System.currentTimeMillis());

		String ct = crypto.encrypt(original, serverKey.getPublicKey());

		// Flip ONE character in the middle -> GCM tag should no longer verify
		char[] chars = ct.toCharArray();
		int mid = chars.length / 2;
		chars[mid] = (chars[mid] == 'A') ? 'B' : 'A';
		String tampered = new String(chars);

		assertThrows(Exception.class, () -> crypto.decrypt(tampered));
	}

	@Test
	void seedsAccountsAndCreatesPacket() throws Exception {
		// seeding happened at startup
		assertTrue(accountRepo.count() >= 4);

		// build a packet like the sender's phone would
		MeshPacket packet = demo.createPacket(
				"ankush@bank", "bob@bank", new BigDecimal("100.00"), "1234", 5);

		assertNotNull(packet.getPacketId());
		assertNotNull(packet.getCiphertext());

		// unlock it to prove it really holds the right payment
		PaymentInstruction inside = crypto.decrypt(packet.getCiphertext());
		assertEquals("ankush@bank", inside.getSenderVpa());
		assertEquals("bob@bank", inside.getReceiverVpa());
		assertEquals(0, new BigDecimal("100.00").compareTo(inside.getAmount()));
	}

	@Test
	void packetSpreadsAcrossMesh() throws Exception {
		mesh.resetMesh();
		mesh.inject("phone-ankush", "ankush@bank", "bob@bank",
				new BigDecimal("100.00"), "1234", 5);
		mesh.gossipOnce();
		assertEquals(5, mesh.snapshot().size());
	}

	@Test
	void firstClaimWinsSecondIsDuplicate() {
		idem.clear();
		assertTrue(idem.claim("abc"));
		assertFalse(idem.claim("abc"));
		assertTrue(idem.isSeen("abc"));
	}

	// @Test
	// void duplicatePacketIsRejected() {
	// idem.clear();

	// // first time -> allowed
	// assertTrue(idem.claim("fingerprint-123"));

	// // second time, same fingerprint -> duplicate
	// assertFalse(idem.claim("fingerprint-123"));

	// // a different packet is fine
	// assertTrue(idem.claim("fingerprint-456"));
	// }

	@Test
	void samePaymentIsOnlyClaimedOnce() throws Exception {
		idem.clear();

		MeshPacket packet = demo.createPacket(
				"ankush@bank", "bob@bank", new BigDecimal("100.00"), "1234", 5);
		String hash = crypto.hashCiphertext(packet.getCiphertext());

		assertTrue(idem.claim(hash), "first delivery should be accepted");
		assertFalse(idem.claim(hash), "second delivery must be rejected");
		assertTrue(idem.isSeen(hash));
	}
}
