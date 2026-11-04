package africa.finishers.identity.domain;

public final class PhoneNumber {
    private PhoneNumber() {}
    public static String normalize(String raw) {
        if(raw==null || raw.length()>40)throw new IllegalArgumentException("Enter a valid phone number");
        String value=raw.strip().replace(" ","").replace("-","").replace("(","").replace(")","");
        if(value.matches("0[67][0-9]{8}"))value="+255"+value.substring(1);
        else if(value.matches("[67][0-9]{8}"))value="+255"+value;
        if(!value.matches("[+][1-9][0-9]{7,14}"))throw new IllegalArgumentException("Enter a valid phone number");
        return value;
    }
}
