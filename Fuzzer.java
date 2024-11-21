import javax.swing.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.Arrays;


public class Fuzzer {

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java Fuzzer.java \"<command_to_fuzz>\"");
            System.exit(1);
        }
        String commandToFuzz = args[0];
        String workingDirectory = "./";

        if (!Files.exists(Paths.get(workingDirectory, commandToFuzz))) {
            throw new RuntimeException("Could not find command '%s'.".formatted(commandToFuzz));
        }

        String seedInput = "<html>\n" +
                "<body>\n" +
                "\n" +
                "<p>aaaa</p>\n" +
                "\n" +
                "<img width=\"500\">\n" +
                "\n" +
                "</body>\n" +
                "</html>\n" +
                "\n";
        List<String> mutatedInputs = getMutatedInputs(seedInput, 10);

        ProcessBuilder builder = getProcessBuilderForCommand(commandToFuzz, workingDirectory);
        System.out.printf("Command: %s\n", builder.command());

        for (String i : mutatedInputs) {
            runCommand(builder, i);
        }
    }

    private static ProcessBuilder getProcessBuilderForCommand(String command, String workingDirectory) {
        ProcessBuilder builder = new ProcessBuilder();
        boolean isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");
        if (isWindows) {
            builder.command("cmd.exe", "/c", command);
        } else {
            builder.command("sh", "-c", command);
        }
        builder.directory(new File(workingDirectory));
        builder.redirectErrorStream(true); // redirect stderr to stdout
        return builder;
    }

    private static void runCommand(ProcessBuilder builder, String input)  {
        try {
            Process process = builder.start();

            OutputStream streamToCommand = process.getOutputStream();
            streamToCommand.write(input.getBytes());
            streamToCommand.flush();
            streamToCommand.close();

            int exitCode = process.waitFor();


            InputStream streamFromCommand = process.getInputStream();
            String output = readStreamIntoString(streamFromCommand);
            streamFromCommand.close();


            if (exitCode != 0) {
                System.out.printf("Input: %s\n", input);
                System.out.printf("Output: %s\n", output
                        // ignore warnings due to usage of gets() in test program
                        .replaceAll("warning: this program uses gets\\(\\), which is unsafe.", "")
                        .trim()
                );
                System.out.println("Non-zero exit code detected!");
                System.out.printf("Exit code: %s\n", exitCode);
                System.out.printf("\n ");

                System.exit(1);

            }
        }
        catch (Exception ex) {
            System.out.println(ex.getStackTrace());
        }

    }

    private static String readStreamIntoString(InputStream inputStream) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        return reader.lines()
                .map(line -> line + System.lineSeparator())
                .collect(Collectors.joining());
    }


    private static List<String> getMutatedInputs(String seedInput, int N) {

        Random random = new Random();
        List<String> mutatedInputs = new ArrayList<>();

        for (int i = 0; i < N; i++) {

            //exchange a character
            StringBuilder mutated = new StringBuilder(seedInput);
            char randomSymbol = mutated.charAt(random.nextInt(seedInput.length()));
            mutated.setCharAt(random.nextInt(seedInput.length()), randomSymbol);
            mutatedInputs.add(mutated.toString());

            //delete part of the string
            mutated = new StringBuilder(seedInput);
            int position = random.nextInt(seedInput.length());
            int randomLen = random.nextInt(seedInput.length() - position) + 1;
            mutated.delete(position, position + randomLen);

            mutatedInputs.add(mutated.toString());


            //repeating a character  in one place
            mutated = new StringBuilder(seedInput);
            int amount = random.nextInt(50);
            String symbols = String.valueOf(mutated.charAt(random.nextInt(seedInput.length()))).repeat(amount);
            position = random.nextInt(seedInput.length());
            mutated.insert(position, symbols);
            mutatedInputs.add(mutated.toString());

        }

        return mutatedInputs;
    }
}
