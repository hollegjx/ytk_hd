package learn.gjx.tuku1;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "picture.draft.cleanup-enabled=false",
        "spring.session.store-type=none"
})
class Tuku1ApplicationTests {

    @Test
    void contextLoads() {
    }

}
