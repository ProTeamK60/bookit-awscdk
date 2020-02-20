package se.knowit.bookit;

import software.amazon.awscdk.core.App;

public class BookitApp {
    public static void main(final String[] args) {
        App app = new App();

        new VpcStack(app, "VpcStack");

        app.synth();
    }
}
