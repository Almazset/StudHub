package kz.studhub.web;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
@Controller public class FriendsPageController { @GetMapping("/friends") public String friends(){return "friends";} }
