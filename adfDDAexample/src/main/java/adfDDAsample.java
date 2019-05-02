import com.amazonaws.AmazonClientException;
import com.amazonaws.services.devicefarm.AWSDeviceFarm;
import com.amazonaws.services.devicefarm.AWSDeviceFarmClientBuilder;
import com.amazonaws.services.devicefarm.model.*;

import java.io.IOException;
import java.io.File;
import java.io.*;

/**
 * Created by karasik on 10/06/2018.
 */
public class adfDDAsample {

    private AWSDeviceFarm adf;
    private String projectArn;
    private String deviceArn ;
    private String sessionArn;
    private String sessionHost;


    private String getProjectArn(String project){

        String projectArn = "";

        ListProjectsRequest listProjectsRequest = new ListProjectsRequest();
        final ListProjectsResult listProjectsResult;

        listProjectsResult = adf.listProjects(listProjectsRequest);

        for (Project p : listProjectsResult.getProjects()) {

            if (p.getName().equals(project)) {
                projectArn = p.getArn();
                System.out.println("Project found: " + projectArn);
                break;
            }
        }

        if (projectArn.length() == 0) {
            CreateProjectRequest createProjectRequest = new CreateProjectRequest()
                    .withName(project);
            CreateProjectResult createProjectResult = adf.createProject(createProjectRequest);
            projectArn = createProjectResult.getProject().getArn();
            System.out.println("Project created: " + projectArn);
        }
        return projectArn;
    }


    private String getDeviceArn(String device){

        String deviceArn = "";
        String FLEET_TYPE = "PRIVATE";


        ListDevicesRequest listDevicesRequest = new ListDevicesRequest();
        final ListDevicesResult listDevicesResult;

        listDevicesResult = adf.listDevices(listDevicesRequest);

        for (Device d : listDevicesResult.getDevices())
            if (d.getFleetType().equals(FLEET_TYPE) && d.getName().equals(device)) {
                deviceArn = d.getArn();
                System.out.println("Device: " + d.getName() + " (" + d.getArn() + ")");
            }

        return deviceArn;
    }


    private void createRemoteSession(String name){

        final String SSH_PUBLIC_KEY = "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQCiH+UBMwiCRrhJ9NJl/NFtqGVsYLquz196up3Hjb3rPfmy1O8baCYIM4+CmJNM2uz7PzJ0CREl06QF/9vGnh5Rn3lGm7jJHv9ZJL6e/kXCxHrSAEjRxqivXcowKpRXDuZoXI+5c+Fdvu1GJqazYfJyH4iSKbD2iS4oTbus5tmdajq0YMDh8AeEL/PNsJrl9vvXvTy0H1nx5Iw4YHMo/XAFLQ5Cguz9g7aGiYmcLZVbUjDbVmsR22+k+wt0hmmYuzResWPnuWXj0k9BJV2dHgniTfkYOe8VG3l0zxBgaIjMYMmqOFijthCmW1GyqpApW9A4lZiQG7o7xek7ZXVlh1mP device-farm";

        String sessionArn = "";

        CreateRemoteAccessSessionRequest request = new CreateRemoteAccessSessionRequest()
                .withProjectArn(projectArn)
                .withDeviceArn(deviceArn)
                .withName(name)
                .withInteractionMode(InteractionMode.NO_VIDEO)
                .withRemoteDebugEnabled(true)
                .withSshPublicKey(SSH_PUBLIC_KEY);

        CreateRemoteAccessSessionResult response = adf.createRemoteAccessSession(request);

        RemoteAccessSession remoteAccessSession;
        remoteAccessSession = response.getRemoteAccessSession();

        sessionArn = remoteAccessSession.getArn();

        System.out.println("Session created: " + sessionArn );

        this.sessionArn = sessionArn;

    }

    private void setSessionHost(){
        GetRemoteAccessSessionRequest remoteAccessSessionRequest = new GetRemoteAccessSessionRequest().withArn(sessionArn);
        sessionHost = adf.getRemoteAccessSession(remoteAccessSessionRequest).getRemoteAccessSession().getHostAddress();
    }

    private boolean waitRemoteSession(int sleep_time, int max_retries) throws InterruptedException{

        String sessionStatus = "PENDING";
        int retries = 0;

        GetRemoteAccessSessionRequest remoteAccessSessionRequest = new GetRemoteAccessSessionRequest().withArn(sessionArn);

        while (!sessionStatus.equals("RUNNING") && ++retries < max_retries){
            System.out.println(String.valueOf(retries * sleep_time/1000) + " sec. " + sessionStatus);
            Thread.sleep(sleep_time);
            sessionStatus = adf.getRemoteAccessSession(remoteAccessSessionRequest).getRemoteAccessSession().getStatus();
        }

        if (retries == max_retries) {
            System.out.println("Session still not running after timeout of " + String.valueOf(max_retries * sleep_time / 1000) + " seconds.");
            return false;
        }
        else{
            //sessionHost = adf.getRemoteAccessSession(remoteAccessSessionRequest).getRemoteAccessSession().getHostAddress();
            System.out.println("Session is running after " + String.valueOf(retries * sleep_time / 1000) + " seconds.");
            return true;
        }
    }

    private void stopRemoteSession(){
        StopRemoteAccessSessionRequest stopRemoteAccessSessionRequest = new StopRemoteAccessSessionRequest().withArn(sessionArn);
        StopRemoteAccessSessionResult stopRemoteAccessSessionResult = adf.stopRemoteAccessSession(stopRemoteAccessSessionRequest);
    }

    private adfDDAsample(String device, String project) throws InterruptedException {

        try {
            adf = AWSDeviceFarmClientBuilder.standard().build();
        } catch (AmazonClientException ace) {
            System.out.println("Caught an AmazonClientException, which means the client encountered "
                    + "a serious internal problem while trying to communicate with DF, "
                    + "such as not being able to access the network.");
            System.out.println("Error Message: " + ace.getMessage());
        }

        this.projectArn = getProjectArn(project);
        this.deviceArn = getDeviceArn(device);
    }


    static void executeCommand(String command) throws IOException, InterruptedException {

        File tempScript = createTempScript(command);

        try {
            ProcessBuilder pb = new ProcessBuilder("bash", tempScript.toString());
            pb.inheritIO();
            Process process = pb.start();
            process.waitFor();
        } finally {
            tempScript.delete();
        }
    }

    static File createTempScript(String command) throws IOException {
        File tempScript = File.createTempFile("script", null);

        Writer streamWriter = new OutputStreamWriter(new FileOutputStream(
                tempScript));
        PrintWriter printWriter = new PrintWriter(streamWriter);

        printWriter.println("#!/bin/bash");
        printWriter.println(command);
        printWriter.close();

        return tempScript;
    }

    public static void main(String[] args) throws InterruptedException{

        final String awsDeviceFarmTunnelCommand = "/Users/karasik/aws-device-farm-tunnel-macos/aws-device-farm-tunnel";
        final String awsDeviceFarmTunnelCommandStart = "start";
        final String awsDeviceFarmTunnelCommandPrivateKey = "~/.aws/devicefarm/prikey.pem";
        final String awsDeviceFarmTunnelCommandOS = "-a -i";
        final String awsDeviceFarmTunnelCommandMacPass = "-p Welc0me11";


        final String PROJECT = "remoteAccessTest";
        final String SESSION_NAME = "remoteDebugSession";

        //Apple iPad Air 2, Apple iPhone 8, Google Pixel 2, LG Nexus 5X, Apple iPhone 7
        final String DEVICE = "Apple iPhone 8";

        int SLEEP_TIME = 10000;
        int MAX_RETRIES = 20;

        adfDDAsample client;
        client = new adfDDAsample(DEVICE,PROJECT);

        if (client.deviceArn.isEmpty()) System.out.println("Failed to get the device!");
        else {
            client.createRemoteSession(SESSION_NAME);
            if (client.sessionArn.isEmpty()) System.out.println("Failed to create remote session!");
            else
            if (client.waitRemoteSession(SLEEP_TIME, MAX_RETRIES)) {
                client.setSessionHost();
            }
        }

        String command =
                awsDeviceFarmTunnelCommand + " " +
                        awsDeviceFarmTunnelCommandStart + " " +
                        awsDeviceFarmTunnelCommandOS + " " +
                        awsDeviceFarmTunnelCommandMacPass + " " +
                        awsDeviceFarmTunnelCommandPrivateKey + " " +
                        client.sessionHost;

        //pass the command to run the bash from the code or open Terminal alternatively:
        String script = "osascript -e 'tell app \"Terminal\" to do script \"" + command + "\"'";
        try {
            executeCommand(script);
        } catch (IOException e) {
            e.printStackTrace();
        }

        //client.stopRemoteSession();
    }
}

