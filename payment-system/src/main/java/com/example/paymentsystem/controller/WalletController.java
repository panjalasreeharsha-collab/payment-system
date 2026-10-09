package com.example.paymentsystem.controller;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.paymentsystem.entity.Transaction;
import com.example.paymentsystem.entity.User;
import com.example.paymentsystem.service.WalletService;
import com.example.paymentsystem.service.WalletService.TransferResult;

@Controller
@RequestMapping
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @GetMapping({"/", "/dashboard"})
    public String dashboard(@RequestParam(required = false) Long userId, Model model) {
        if (userId != null) {
            User user = walletService.findUser(userId).orElse(null);
            if (user == null) {
                model.addAttribute("error", "That account could not be found.");
            } else {
                model.addAttribute("user", user);
                model.addAttribute("balance", walletService.getBalance(userId));
                model.addAttribute("transactions", walletService.getRecentTransactions(userId));
            }
        }
        model.addAttribute("historyView", false);
        return "dashboard";
    }

    @GetMapping("/transactions")
    public String transactionHistory(@RequestParam Long userId, Model model) {
        User user = walletService.findUser(userId).orElse(null);
        if (user == null) {
            model.addAttribute("error", "That account could not be found.");
            model.addAttribute("historyView", true);
            return "dashboard";
        }

        model.addAttribute("user", user);
        model.addAttribute("balance", walletService.getBalance(userId));
        model.addAttribute("transactions", walletService.getTransactionHistory(userId));
        model.addAttribute("historyView", true);
        return "dashboard";
    }

    @PostMapping("/register")
    public String register(@RequestParam String name, @RequestParam String phoneNumber,
            @RequestParam String upiId, @RequestParam String password, RedirectAttributes redirectAttributes) {
        try {
            User user = walletService.registerUser(name, phoneNumber, upiId, password);
            return "redirect:/dashboard?userId=" + user.getId();
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        } catch (DataIntegrityViolationException exception) {
            redirectAttributes.addFlashAttribute("error", "That phone number or UPI ID is already registered.");
        }
        return "redirect:/dashboard";
    }

    @PostMapping("/add-funds")
    public String addFunds(@RequestParam Long userId, @RequestParam java.math.BigDecimal amount,
            RedirectAttributes redirectAttributes) {
        try {
            walletService.addFunds(userId, amount);
            redirectAttributes.addFlashAttribute("success", "Money added to your wallet.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return redirectToDashboard(userId);
    }

    @PostMapping("/send")
    public String sendMoney(@RequestParam("userId") Long senderUserId,
            @RequestParam("recipientUpiId") String recipientUpiId,
            @RequestParam("amount") java.math.BigDecimal amount, RedirectAttributes redirectAttributes) {
        TransferResult result = walletService.transferFunds(senderUserId, recipientUpiId, amount);
        redirectAttributes.addFlashAttribute(result.successful() ? "success" : "error", result.message());
        return redirectToDashboard(senderUserId);
    }

    private static String redirectToDashboard(Long userId) {
        return "redirect:/dashboard?userId=" + userId;
    }
}