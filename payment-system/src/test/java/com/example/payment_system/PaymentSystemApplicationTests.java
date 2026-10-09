package com.example.payment_system;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.example.paymentsystem.entity.Wallet;
import com.example.paymentsystem.entity.User;
import com.example.paymentsystem.repository.UserRepository;
import com.example.paymentsystem.repository.WalletRepository;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:payment_system_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureMockMvc
class PaymentSystemApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private WalletRepository walletRepository;

	@Autowired
	private UserRepository userRepository;

	@Test
	void contextLoads() {
	}

	@Test
	void walletPaymentsUpdateBalancesAndRenderTransactionStatus() throws Exception {
		MvcResult senderRegistration = mockMvc.perform(post("/register")
				.param("name", "Asha Rao")
				.param("phoneNumber", "9876543210")
				.param("upiId", "asha@bank")
				.param("password", "strong-pass-123"))
			.andExpect(status().is3xxRedirection())
			.andReturn();

		String senderId = userIdFrom(senderRegistration);
		mockMvc.perform(get("/dashboard").param("userId", senderId))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("10000.00")));

		Wallet senderWallet = walletRepository.findByUser_Id(Long.valueOf(senderId)).orElseThrow();
		walletRepository.deleteById(senderWallet.getId());
		mockMvc.perform(get("/dashboard").param("userId", senderId))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("10000.00")));

		mockMvc.perform(post("/add-funds").param("userId", senderId).param("amount", "125.50"))
			.andExpect(status().is3xxRedirection());

		MvcResult recipientRegistration = mockMvc.perform(post("/register")
				.param("name", "Ravi Kumar")
				.param("phoneNumber", "9876543211")
				.param("upiId", "ravi@bank")
				.param("password", "strong-pass-456"))
			.andExpect(status().is3xxRedirection())
			.andReturn();
		String recipientId = userIdFrom(recipientRegistration);
			User recipient = userRepository.findById(Long.valueOf(recipientId)).orElseThrow();
			recipient.setUpiId("  RAVI@Bank  ");
			userRepository.saveAndFlush(recipient);
		Wallet recipientWallet = walletRepository.findByUser_Id(Long.valueOf(recipientId)).orElseThrow();
		walletRepository.deleteById(recipientWallet.getId());

		mockMvc.perform(post("/send").param("userId", senderId)
					.param("recipientUpiId", "  RAVI@BANK  ").param("amount", "50.00"))
			.andExpect(status().is3xxRedirection());

		mockMvc.perform(get("/dashboard").param("userId", senderId))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("10075.50")))
			.andExpect(content().string(containsString("ravi@bank")));

		mockMvc.perform(post("/send").param("userId", senderId)
				.param("recipientUpiId", "ravi@bank").param("amount", "20000.00"))
			.andExpect(status().is3xxRedirection());

		mockMvc.perform(get("/dashboard").param("userId", senderId))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("10075.50")))
			.andExpect(content().string(containsString("FAILED")));

		mockMvc.perform(post("/send").param("userId", senderId)
				.param("recipientUpiId", "nobody@bank").param("amount", "1.00"))
			.andExpect(status().is3xxRedirection())
			.andExpect(flash().attribute("error", "Recipient account was not found for UPI ID: nobody@bank."));

		mockMvc.perform(get("/dashboard").param("userId", recipientId))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("10050.00")))
			.andExpect(content().string(containsString("asha@bank")));
	}

	private static String userIdFrom(MvcResult registration) {
		String redirectUrl = registration.getResponse().getRedirectedUrl();
		return redirectUrl.substring(redirectUrl.indexOf("userId=") + "userId=".length());
	}

}
