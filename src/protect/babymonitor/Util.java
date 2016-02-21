package protect.babymonitor;

import java.util.Random;

public class Util {
	public static String generatePassword(int length) {
		char[] allowedChars = ("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
				+ "1234567890"
				+ "|[]()+=-_!#@$%&*?")
				.toCharArray();
        StringBuilder sb1 = new StringBuilder();
        Random random1 = new Random();
        for (int i = 0; i < length; i++)
        {
            sb1.append(allowedChars[random1.nextInt(allowedChars.length)]);
        }
       
        return sb1.toString();  
	}
}
