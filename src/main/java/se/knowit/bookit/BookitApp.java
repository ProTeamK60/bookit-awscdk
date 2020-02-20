package se.knowit.bookit;

import software.amazon.awscdk.core.App;

public class BookitApp {
    public static void main(final String[] args) {
        App app = new App();

        new BookitCommonStack(app, "BookitCommonStack");

        app.synth();
    }
}
