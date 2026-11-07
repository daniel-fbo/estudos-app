package br.estudos.infra.google;

import br.estudos.app.ports.CalendarClient;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.CalendarListEntry;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/**
 * Google Calendar (cliente oficial). Credenciais do cliente OAuth ("app para computador") em
 * {@code <dir>/credentials.json}; token em {@code <dir>/tokens/}. {@code dir} = {@code ~/.estudos-app/google}.
 */
public class GoogleCalendarClient implements CalendarClient {
    public static final String CALENDAR_NAME = "Estudos";
    private static final GsonFactory JSON = GsonFactory.getDefaultInstance();

    private final Path dir;
    private volatile Calendar service;
    private volatile String calendarId;

    public GoogleCalendarClient(Path dir) { this.dir = dir; }

    public Path credentialsFile() { return dir.resolve("credentials.json"); }
    private Path tokensDir() { return dir.resolve("tokens"); }

    @Override
    public boolean authorized() {
        if (!Files.isRegularFile(credentialsFile())) return false;
        try (var s = Files.list(tokensDir())) {
            return s.findAny().isPresent();
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public synchronized void authorize() throws CalendarException {
        service();
    }

    private synchronized Calendar service() throws CalendarException {
        if (service != null) return service;
        if (!Files.isRegularFile(credentialsFile()))
            throw new CalendarException("Credenciais não encontradas em " + credentialsFile());
        try {
            NetHttpTransport transport = GoogleNetHttpTransport.newTrustedTransport();
            GoogleClientSecrets secrets;
            try (Reader r = Files.newBufferedReader(credentialsFile())) {
                secrets = GoogleClientSecrets.load(JSON, r);
            }
            Files.createDirectories(tokensDir());
            GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                    transport, JSON, secrets, List.of(CalendarScopes.CALENDAR))
                    .setDataStoreFactory(new FileDataStoreFactory(tokensDir().toFile()))
                    .setAccessType("offline").build();
            Credential cred = new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver.Builder().setPort(0).build())
                    .authorize("user");
            service = new Calendar.Builder(transport, JSON, cred).setApplicationName("Estudos App").build();
            return service;
        } catch (Exception e) {
            throw new CalendarException("Falha na autorização do Google: " + e.getMessage(), e);
        }
    }

    private String calendarId(Calendar svc) throws IOException {
        if (calendarId != null) return calendarId;
        for (CalendarListEntry e : svc.calendarList().list().execute().getItems())
            if (CALENDAR_NAME.equals(e.getSummary())) return calendarId = e.getId();
        var created = svc.calendars().insert(new com.google.api.services.calendar.model.Calendar()
                .setSummary(CALENDAR_NAME)).execute();
        return calendarId = created.getId();
    }

    @Override
    public String createEvent(String title, String description, Instant start, Instant end) throws CalendarException {
        Calendar svc = service();
        try {
            Event ev = new Event().setSummary(title).setDescription(description)
                    .setStart(new EventDateTime().setDateTime(new DateTime(start.toEpochMilli())))
                    .setEnd(new EventDateTime().setDateTime(new DateTime(end.toEpochMilli())));
            return svc.events().insert(calendarId(svc), ev).execute().getId();
        } catch (IOException e) {
            throw new CalendarException("Falha ao criar evento: " + e.getMessage(), e);
        }
    }
}
