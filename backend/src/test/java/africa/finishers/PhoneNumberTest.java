package africa.finishers;

import africa.finishers.identity.domain.PhoneNumber;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PhoneNumberTest {
    @Test void normalizesEquivalentTanzanianFormats(){
        assertThat(PhoneNumber.normalize("0712 345 678")).isEqualTo("+255712345678");
        assertThat(PhoneNumber.normalize("712345678")).isEqualTo("+255712345678");
        assertThat(PhoneNumber.normalize("+255 (712) 345-678")).isEqualTo("+255712345678");
    }
    @Test void preservesInternationalNumbers(){assertThat(PhoneNumber.normalize("+447123456789")).isEqualTo("+447123456789");}
    @Test void rejectsLettersOrMalformedNumbers(){
        assertThatThrownBy(()->PhoneNumber.normalize("call-0712345678")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->PhoneNumber.normalize("+0")).isInstanceOf(IllegalArgumentException.class);
    }
}
