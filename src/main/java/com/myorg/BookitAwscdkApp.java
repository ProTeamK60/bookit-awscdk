package com.myorg;

import software.amazon.awscdk.core.App;

import java.util.Arrays;

public class BookitAwscdkApp {
    public static void main(final String[] args) {
        App app = new App();

        new BookitAwscdkStack(app, "BookitAwscdkStack");

        app.synth();
    }
}
