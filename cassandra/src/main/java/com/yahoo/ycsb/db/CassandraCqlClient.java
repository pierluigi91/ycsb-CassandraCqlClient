package com.yahoo.ycsb.db;



import java.nio.ByteBuffer;
import java.util.*;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ColumnDefinitions;
import com.datastax.driver.core.Host;
import com.datastax.driver.core.Metadata;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;




import com.datastax.driver.core.SimpleStatement;
import com.datastax.driver.core.Statement;
import com.datastax.driver.core.querybuilder.Insert;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.datastax.driver.core.querybuilder.Select;
import com.yahoo.ycsb.ByteArrayByteIterator;
import com.yahoo.ycsb.ByteIterator;
import com.yahoo.ycsb.DB;
import com.yahoo.ycsb.DBException;

public class CassandraCqlClient extends DB{

	private static Cluster cluster = null;
	private static Session session = null;
	private static UUID uuidGenerato = UUID.randomUUID();

	public void init() throws DBException { //il node è 127.0.0.1
		try{
			String host = "127.0.0.1";

			cluster = Cluster.builder().addContactPoints(host).build();

			Metadata metadata = cluster.getMetadata();
			System.out.printf("Connected to cluster: %s\n", metadata.getClusterName());

			for (Host discoveredHost : metadata.getAllHosts()) {
				System.out.printf("Datacenter: %s; Host: %s; Rack: %s\n",
						discoveredHost.getDatacenter(),
						discoveredHost.getAddress(),
						discoveredHost.getRack());
			}

			String keyspace = getProperties().getProperty("cassandra.keyspace", "simplex");

			session = cluster.connect(keyspace);

		} catch (Exception e) {
			throw new DBException(e);
		}
	}





	@Override
	public int read(String table, String key, Set<String> fields, HashMap<String, ByteIterator> result) {
		try {



			String cqlQuery = "SELECT * FROM " +
					getProperties().getProperty("cassandra.keyspace", "simplex") +
					"." + table + " " + " WHERE " + "id" + " = ?" +"LIMIT 1;";
			PreparedStatement select = session.prepare(cqlQuery);
			BoundStatement selectStatement = new BoundStatement(select);
			ResultSet rs = session.execute(selectStatement.bind( UUID.fromString(key) ) );

			
			
			System.out.println(String.format("%-30s\t%-30s\t%-30s\n%s", "title",
					"album", "artist", "-------------------------------+------------------------+-------"));
			for (Row row : rs) {
				System.out.println(String.format("%-30s\t%-30s\t%-30s",
						row.getString("title"),
						row.getString("album"), row.getString("artist")));
			}
			System.out.println();

			//Sarà ovviamente per il limit, una sola riga
			if (!rs.isExhausted()) { //entra qui solo se rs non ha piu di un risultato
				Row row = rs.one(); 
				ColumnDefinitions cd = row.getColumnDefinitions(); // si salva le colonne della row

				for (ColumnDefinitions.Definition def : cd) {
					ByteBuffer val = row.getBytesUnsafe(def.getName());//se def.getName() è null, sarà null anche val
					if (val != null) { 
						result.put(def.getName(), new ByteArrayByteIterator(val.array())); //non si può istanziare ByteIterator
						//quindi abbiamo fatto: val è un ByteBuffer, noi abbiamo bisogno di 
						//un array di Byte, quindi .array()
					}
					else { 
						result.put(def.getName(), null);
					}
				}

			}

			return 0;

		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Errore nella lettura della key: " + key);
			return -1;
		}
	}

	@Override
	public int scan(String table, String startkey, int recordcount, 
			Set<String> fields, Vector<HashMap<String, ByteIterator>> result) {
		try {
			String cqlQuery = "SELECT * FROM " +
					getProperties().getProperty("cassandra.keyspace", "simplex") +
					"." + table + " " + " WHERE " + "token(id)" + " >= token(?)" +" LIMIT " + recordcount + ";";
			System.out.println(cqlQuery);

			PreparedStatement select = session.prepare(cqlQuery);
			BoundStatement selectStatement = new BoundStatement(select);
			ResultSet rs = session.execute(selectStatement.bind( UUID.fromString(startkey) ) );


			System.out.println(String.format("%-30s\t%-30s\t%-30s\n%s", "title",
					"album", "artist", "-------------------------------+------------------------+-------"));
			for (Row row : rs) {
				System.out.println(String.format("%-30s\t%-30s\t%-30s",
						row.getString("title"),
						row.getString("album"), row.getString("artist")));
			}
			System.out.println();

			HashMap<String, ByteIterator> tuple;

			while (!rs.isExhausted()) {
				Row row = rs.one();
				tuple = new HashMap<String, ByteIterator> ();

				ColumnDefinitions cd = row.getColumnDefinitions();

				for (ColumnDefinitions.Definition def : cd) {
					ByteBuffer val = row.getBytesUnsafe(def.getName());
					if (val != null) {
						tuple.put(def.getName(),
								new ByteArrayByteIterator(val.array()));
					}
					else {
						tuple.put(def.getName(), null);
					}
				}

				result.add(tuple);
			}

			return 0;

		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Errore nello scan della startkey: " + startkey);
			return -1;
		}
	}
	@Override
	public int update(String table, String key, HashMap<String, ByteIterator> values) {
		return insert(table, key, values);
	}


	@Override
	public int insert(String table, String key, HashMap<String, ByteIterator> values) {
		try {
			
			
			
			Insert insertStmt = QueryBuilder.insertInto(table); //Statement specifico per l'inserimento

			//aggiunge la chiave
			insertStmt.value("id", java.util.UUID.fromString(key));

			//aggiunge i campi
			for (Map.Entry<String, ByteIterator> entry : values.entrySet()) {
				Object value;
				
				ByteIterator byteIterator = entry.getValue();
				value = byteIterator.toString();
				insertStmt.value(entry.getKey(), value);
			}
			
			session.execute(insertStmt);
			
			return 0;
		} catch (Exception e) {
			e.printStackTrace();
		}

		return -1;
	}
	@Override
	public int delete(String table, String key) {
		try {
			
			
			String cqlQuery = "DELETE " + "FROM " + getProperties().getProperty("cassandra.keyspace", "simplex")
					+ "." + table +" WHERE " + "id = " + key + ";";
			System.out.println(cqlQuery);
			PreparedStatement statement = session.prepare(cqlQuery);
			BoundStatement boundStatement = new BoundStatement(statement);
			session.execute(boundStatement);
			
			return 0;
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Errore nel cancellare la key: " + key);
		}

		return -1;
	}
	
	public UUID getUuidGenerato() {
		return uuidGenerato;
	}





	public void setUuidGenerato(UUID uuidGenerato) {
		this.uuidGenerato = uuidGenerato;
	}

	public static void main(String[] args) throws DBException{
		CassandraCqlClient cql = new CassandraCqlClient();
		cql.init();
		Set<String> fields = null; //null perchè nel log abbiamo visto che legge tutti i fields, quindi non c'è bisogno di specificarne solo alcuni
		HashMap<String, ByteIterator> result = new HashMap<String, ByteIterator>();
		//cql.read("songs", "756716f7-2e54-4715-9f00-91dcbea6cf50", fields, result);
		Vector<HashMap<String, ByteIterator>> result2 = new Vector<HashMap<String, ByteIterator>>();
		//cql.scan("songs", "756716f7-2e54-4715-9f00-91dcbea6cf50", 1, fields, result2);
		HashMap<String, ByteIterator> values = null;
		//cql.insert("songs", uuidGenerato.toString(), values);
		//cql.delete("songs", "756716f7-2e54-4715-9f00-91dcbea6cf50");
		//cql.read("songs",  "756716f7-2e54-4715-9f00-91dcbea6cf50", fields, result);
		HashMap<String, ByteIterator> valori = new HashMap<String, ByteIterator>();
		
		valori.put("title", new ByteArrayByteIterator("Alba Chiara".getBytes()));
		valori.put("album", new ByteArrayByteIterator("Blasco".getBytes()));
		valori.put("artist", new ByteArrayByteIterator("Vasco Rossi".getBytes()));
	
		//cql.insert("songs", uuidGenerato.toString(), valori);
	
		//cql.scan("songs", "756716f7-2e54-4715-9f00-91dcbea6cf50", 100, fields, result2);
		
		HashMap<String, ByteIterator> valori2 = new HashMap<String, ByteIterator>();
		
		valori2.put("title", new ByteArrayByteIterator("3 Minuti".getBytes()));
		valori2.put("album", new ByteArrayByteIterator("3 Minuti".getBytes()));
		valori2.put("artist", new ByteArrayByteIterator("Negramaro".getBytes()));
		
		cql.insert("songs", uuidGenerato.toString(), valori2);
		
	}

}




