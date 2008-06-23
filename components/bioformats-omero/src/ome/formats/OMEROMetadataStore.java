/*
 * ome.formats.OMEROMetadataStore
 *
 *------------------------------------------------------------------------------
 *  Copyright (C) 2006-2008 University of Dundee. All rights reserved.
 *
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *  
 *  You should have received a copy of the GNU General Public License along
 *  with this program; if not, write to the Free Software Foundation, Inc.,
 *  51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *------------------------------------------------------------------------------
 */

package ome.formats;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import loci.formats.meta.MetadataStore;
import ome.api.IQuery;
import ome.api.IUpdate;
import ome.api.RawFileStore;
import ome.api.RawPixelsStore;
import ome.model.IObject;
import ome.model.acquisition.Arc;
import ome.model.acquisition.Detector;
import ome.model.acquisition.DetectorSettings;
import ome.model.acquisition.Filament;
import ome.model.acquisition.ImagingEnvironment;
import ome.model.acquisition.Instrument;
import ome.model.acquisition.Laser;
import ome.model.acquisition.LightSource;
import ome.model.acquisition.OTF;
import ome.model.acquisition.Objective;
import ome.model.acquisition.StageLabel;
import ome.model.annotations.BooleanAnnotation;
import ome.model.annotations.PixelsAnnotationLink;
import ome.model.containers.Dataset;
import ome.model.containers.DatasetImageLink;
import ome.model.containers.Project;
import ome.model.core.Channel;
import ome.model.core.Image;
import ome.model.core.LogicalChannel;
import ome.model.core.OriginalFile;
import ome.model.core.Pixels;
import ome.model.core.PixelsDimensions;
import ome.model.core.PlaneInfo;
import ome.model.enums.AcquisitionMode;
import ome.model.enums.ArcType;
import ome.model.enums.ContrastMethod;
import ome.model.enums.DetectorType;
import ome.model.enums.DimensionOrder;
import ome.model.enums.FilamentType;
import ome.model.enums.Format;
import ome.model.enums.FrequencyMultiplication;
import ome.model.enums.Illumination;
import ome.model.enums.Immersion;
import ome.model.enums.LaserMedium;
import ome.model.enums.LaserType;
import ome.model.enums.PhotometricInterpretation;
import ome.model.enums.PixelsType;
import ome.model.enums.Pulse;
import ome.model.meta.Experimenter;
import ome.model.stats.StatsInfo;
import ome.parameters.Parameters;
import ome.system.Login;
import ome.system.Server;
import ome.system.ServiceFactory;
import ome.api.IRepositoryInfo;
import ome.conditions.SessionException;
import ome.formats.importer.MetaLightSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * An OMERO metadata store. This particular metadata store requires the user to
 * be logged into OMERO prior to use with the {@link #login()} method. While
 * attempts have been made to allow the caller to switch back and forth between 
 * Images and Pixels during metadata population it is <b>strongly</b> 
 * encouraged that at least Images and Pixels are populated in ascending order. 
 * For example: Image_1 --> Pixels_1, Pixels_2 followed by Image_2 --> Pixels_1,
 * Pixels2, Pixels_3.
 * 
 * @author Brian W. Loranger brain at lifesci.dundee.ac.uk
 * @author Chris Allan callan at blackcat.ca
 */
public class OMEROMetadataStore implements MetadataStore
{
    /** Logger for this class. */
    private static Log     log    = LogFactory.getLog(OMEROMetadataStore.class);

    /** OMERO service factory; all other services are retrieved from here. */
    private ServiceFactory sf;

    /** OMERO raw pixels service */
    private RawPixelsStore pservice;

    /** OMERO query service */
    private IQuery         iQuery;

    /** OMERO update service */
    private IUpdate        iUpdate;

    private IRepositoryInfo iInfo;

    /** The "root" image object */
    private List<Image> imageList = new ArrayList<Image>();

    /** An list of Pixels that we have worked on ordered by first access. */
    private List<Pixels> pixelsList = new ArrayList<Pixels>();

    /** A list of lightsource objects */
    private List<Instrument> instrumentList = new ArrayList<Instrument>();

    /** A list of lightsource objects */
    private Map<String, IObject> lsidMap = new HashMap<String, IObject>();


    /** 
     * PlaneInfo ordered cache which compensates for pixels.planeInfo being a
     * HashMap.
     */
    private Map<Pixels, List<PlaneInfo>> planeInfoCache = null;

    private Experimenter    exp;

    private RawFileStore    rawFileStore;

    private Timestamp creationTimestamp;

    private String currentLSID;

    private String oldUUID;
    
    private Server server;
    private Login login;

    /**
     * Creates a new instance.
     * 
     * @param username the username to use to login to the OMERO server.
     * @param password the password to use to login to the OMERO server.
     * @param host the hostname of the OMERO server.
     * @param port the port the OMERO server is listening on.
     * @throws MetadataStoreException if the login credentials are
     *             incorrect or there is another error instantiating required
     *             services.
     */
    public OMEROMetadataStore(String username, String password, String host,
            String port) throws Exception
            {
        // Mask the password information for display in the debug window
        String maskedPswd = "";
        if (password == null) password = new String("");
        if (password.length() > 0) maskedPswd = "<" +password.length() + "chars>";
        else maskedPswd = "<empty>";
        log.debug(String.format("Initializing store: %s/%s %s:%s", 
                username, maskedPswd, host, port));

        // Attempt to log in
        try
        {
            server = new Server(host, Integer.parseInt(port));
            login = new Login(username, password);
            // Instantiate our service factory
            sf = new ServiceFactory(server, login);

            InitializeServices(sf);
            
            oldUUID = sf.getAdminService().getEventContext().getCurrentSessionUuid();

            exp = iQuery.findByString(Experimenter.class, "omeName", username);
        } catch (Throwable t)
        {
            throw new Exception(t);
        }
            }

    /* Makes sure SF is still alive */
    public void checkSF()
    {
        try {
            // first try to automatically reconnect to the server
            iQuery.findByString(Experimenter.class, "omeName", login.getName());
        } 
        catch (SessionException e)
        {
            log.debug(String.format("Session error, attempting a reconnect...."));
            sf = new ServiceFactory(server, login);
            InitializeServices(sf);
            try {
                // test again
                iQuery.findByString(Experimenter.class, "omeName", login.getName());
                log.debug(String.format(" Reconnect successful."));
            }
            catch (SessionException e2)
            {
                log.debug(String.format(" Reconnect failed!"));                
            }
        }
    }

    
    /**
     * Creates a new instance.
     * 
     * @param factory a non-null, active {@link ServiceFactory}
     * @throws MetadataStoreException if the factory is null or there
     *             is another error instantiating required services.
     */
    public OMEROMetadataStore(ServiceFactory factory)
    throws Exception
    {
        if (factory == null)
            throw new Exception(
            "Factory argument cannot be null.");

        sf = factory;

        try
        {
            // Now initialize all our services
            InitializeServices(sf);

        } catch (Throwable t)
        {
            throw new Exception(t);
        }
    }


    /**
     * Private class used by constructor to initialze the services of the service factory.
     * 
     * @param factory a non-null, active {@link ServiceFactory}
     */
    private void InitializeServices(ServiceFactory sf)
    {
        // Now initialize all our services
        iQuery = sf.getQueryService();
        iUpdate = sf.getUpdateService();
        pservice = sf.createRawPixelsStore();
        rawFileStore = sf.createRawFileStore();
        iInfo = sf.getRepositoryInfoService();
    }


    /*
     * (non-Javadoc)
     * 
     * @see loci.formats.MetadataStore#getRoot()
     */
    public Object getRoot()
    {
        return imageList;
    }

    /*
     * (non-Javadoc)
     * 
     * @see loci.formats.MetadataStore#createRoot()
     */
    public void createRoot()
    {
        imageList = new ArrayList<Image>();
        pixelsList = new ArrayList<Pixels>();
        planeInfoCache = new HashMap<Pixels, List<PlaneInfo>>();

        /* Create a new instrument list */
        instrumentList = new ArrayList<Instrument>();
        
        lsidMap = new HashMap<String, IObject>();
        currentLSID =  null;
        
    }

    /*
     * (non-Javadoc)
     * 
     * @see loci.formats.MetadataStore#setRoot(java.lang.Object)
     */
    @SuppressWarnings("unchecked")
    public void setRoot(Object root) throws IllegalArgumentException
    {
        if (!(root instanceof List))
            throw new IllegalArgumentException("'root' object of type '"
                    + root.getClass()
                    + "' must be of type 'List<ome.model.core.Image>'");
        imageList = (List<Image>) root;
    }

    /**
     * Retrieves a server side enumeration.
     * 
     * @param klass the enumeration's class from <code>ome.model.enum</code>
     * @param value the enumeration's string value.
     * @return enumeration object.
     */
    private IObject getEnumeration(Class<? extends IObject> klass, String value)
    {
        checkSF();
        if (klass == null)
            throw new NullPointerException("Expecting not-null klass.");
        if (value == null) return null;

        IObject enumeration = iQuery.findByString(klass, "value", value);

        if (enumeration == null)
            throw new EnumerationException("Problem finding enumeration: ",
                    klass, value);
        return enumeration;
    }

    public long getExperimenterID()
    {
        return exp.getId();
    }


    /**
     * This method maps an existing ome lsid to the lsidMap as well
     * as setting the currentID to the ome lsid.
     * 
     * @param id
     */
    private void mapLSID(String id)
    {
        if (!lsidMap.containsKey(id))
        {
            currentLSID = id;
            lsidMap.put(id,null);
        }
    }
    
    /*
     * (non-Javadoc)
     * 
     * @see loci.formats.MetadataStore#setChannelGlobalMinMax(int,
     *      java.lang.Double, java.lang.Double, java.lang.Integer)
     */
    @SuppressWarnings("unchecked")
    public void setChannelGlobalMinMax(int channelIdx, Double globalMin,
            Double globalMax, Integer pixelsIndex)
    {
        log.debug(String.format(
                "Setting Pixels[%d] Channel[%d] globalMin: '%f' globalMax: '%f'",
                pixelsIndex, channelIdx, globalMin, globalMax));
        if (globalMin != null)
        {
            globalMin = new Double(Math.floor(globalMin.doubleValue()));
        }
        if (globalMax != null)
        {
            globalMax = new Double(Math.ceil(globalMax.doubleValue()));
        }
        StatsInfo statsInfo = new StatsInfo();
        statsInfo.setGlobalMin(globalMin);
        statsInfo.setGlobalMax(globalMax);
        getPixels(pixelsIndex).getChannel(channelIdx).setStatsInfo(statsInfo);
    }

    /**
     * Writes a set of bytes as a plane in the OMERO image repository.
     * 
     * @param id the primary <i>id</i> of the pixels set.
     * @param pixels an array of bytes (sizeX * sizeY * bytesPerPixel)
     * @param theZ the optical section in the pixels array.
     * @param theC the channel in the pixels array.
     * @param theT the timepoint in the pixels array.
     */
    public void setPlane(Long id, byte[] pixels, int theZ, int theC, int theT)
    {
        if (pservice == null) pservice = sf.createRawPixelsStore();

        pservice.setPlane(pixels, theZ, theC, theT);
    }


    /**
     * Sets the pixels id in the OMERO image repository.
     * 
     * @param id the primary <i>id</i> of the pixels set.
     */
    public void setPixelsId(Long id)
    {
        if (pservice == null) pservice = sf.createRawPixelsStore();

        pservice.setPixelsId(id);
    }

    /**
     * Writes a set of bytes as a stack in the OMERO image repository.
     * 
     * @param id he primary <i>id</i> of the pixels set.
     * @param pixels an array of bytes (sizeX * sizeY * sizeZ * bytesPerPixel)
     * @param theC the channel in the pixels array.
     * @param theT the timepoint in the pixels array.
     */
    public void setStack(Long id, byte[] pixels, int theC, int theT)
    {
        if (pservice == null) pservice = sf.createRawPixelsStore();

        pservice.setPixelsId(id);
        pservice.setStack(pixels, theT, theC, theT);
    }

    /**
     * Adds an image to a dataset.
     * 
     * @param image The image to link to <code>dataset</code>.
     * @param dataset The dataset to link to <code>image</code>.
     */
    public void addImageToDataset(Image image, Dataset dataset)
    {
        Image unloadedImage = new Image(image.getId(), false);
        Dataset unloadedDataset = new Dataset(dataset.getId(), false);
        DatasetImageLink link = new DatasetImageLink();
        link.setParent(unloadedDataset);
        link.setChild(unloadedImage);

        // Now update the dataset object in the database
        iUpdate.saveObject(link);
    }

    public void addBooleanAnnotationToPixels(BooleanAnnotation ba, Pixels p)
    {
        Pixels unloadedPixels = new Pixels(p.getId(), false);
        PixelsAnnotationLink link = new PixelsAnnotationLink();
        link.setParent(unloadedPixels);
        link.setChild(ba);
        iUpdate.saveObject(link);

    }

    /**
     * Retrieves dataset names of the current user from the active OMERO
     * instance.
     * @param project the project to retireve datasets from. 
     * @return an array of dataset names.
     */
    public List<Dataset> getDatasets(Project project)
    {
        checkSF();
        List<Dataset> l = iQuery.findAllByQuery(
                "from Dataset where id in " +
                "(select link.child.id from ProjectDatasetLink link where " +
                "link.parent.id = :id)", new Parameters().addId(project.getId()));
        return (List<Dataset>) l;

        // Use this for M3 build till it gets fixed if this is needed.
        //return new ArrayList();
    }

    public Dataset getDataset(long datasetID)
    {
        checkSF();
        Dataset dataset = iQuery.get(Dataset.class, datasetID);
        return dataset;
    }

    public Project getProject(long projectID)
    {
        checkSF();
        Project project = iQuery.get(Project.class, projectID);
        return project;
    }

    /**
     * Retrieves dataset names of the current user from the active OMERO
     * instance.
     * 
     * @return an array of dataset names.
     */
    public List<Project> getProjects()
    {
        checkSF();
        List<Project> l = iQuery.findAllByQuery(
                "from Project as p left join fetch p.datasetLinks " +
                "where p.details.owner.id = :id", 
                new Parameters().addId(exp.getId()));
        return (List<Project>) l;
    }

    /**
     * Saves the current <i>root</i> pixelsList to the database.
     */
    public List<Pixels> saveToDB()
    {
        checkSF();
        IUpdate update = sf.getUpdateService();
        Image[] imageArray = imageList.toArray(new Image[imageList.size()]);
        IObject[] o = update.saveAndReturnArray(imageArray);
        pixelsList = new ArrayList<Pixels>();
        for (int i = 0; i < o.length; i++)
        {
            Image image = (Image) o[i];
            // FIXME: This assumes only *one* pixels set.
            pixelsList.add((Pixels) image.iteratePixels().next());
            imageList.set(i, image);
        }
        return pixelsList;
    }

    public ServiceFactory getSF()
    {
        checkSF();
        return sf;
    }

    public IUpdate getIUpdate()
    {
        checkSF();
        return iUpdate;
    }
    
    /**
     * Links a set of original files to all Pixels that the metadata store
     * currently knows about. NOTE: Ensure that you call this <b>after</b>
     * fully populating the metadata store.
     * @param files The list of File objects to translate to OriginalFile
     * objects and link.
     * @param formatString 
     */
    public void setOriginalFiles(File[] files, String formatString)
    {
        for (File file: files)
        {
            Format f = iQuery.findByString(Format.class, "value", formatString);
            OriginalFile oFile = new OriginalFile();
            oFile.setName(file.getName());
            oFile.setPath(file.getAbsolutePath());
            oFile.setSize(file.length());
            oFile.setSha1("pending");
            oFile.setFormat(f);
            // TODO: There is no creation or access time in Java, will have to find a solution
            Timestamp mTime = new Timestamp(file.lastModified());
            oFile.setAtime(mTime);
            if (creationTimestamp != null)
                oFile.setCtime(creationTimestamp);
            else
                oFile.setCtime(mTime);
            oFile.setMtime(mTime);

            for (Pixels pixels : pixelsList)
            {
                pixels.linkOriginalFile(oFile);
            }
        }
    }

    public void writeFilesToFileStore(File[] files, long pixelsId)
    {
        try
        {
            for (File file : files)
            {
                MessageDigest md;

                try {
                    md = MessageDigest.getInstance("SHA-1");
                } catch (NoSuchAlgorithmException e) {
                    throw new RuntimeException(
                    "Required SHA-1 message digest algorithm unavailable.");
                }

                Parameters p = new Parameters();
                p.addId(pixelsId);
                p.addString("path", file.getAbsolutePath());
                OriginalFile o = iQuery.findByQuery(
                        "select ofile from OriginalFile as ofile left join " +
                        "ofile.pixelsFileMaps as pfm left join pfm.child as child " +
                        "where child.id = :id and ofile.path =:path", p);

                if (o == null) throw 
                new FileNotFoundException("Unable to look up originalFile");

                rawFileStore.setFileId(o.getId());

                byte[] buf = new byte[262144];            
                FileInputStream stream = new FileInputStream(file);

                long pos = 0;
                int rlen;
                while((rlen = stream.read(buf)) > 0)
                {
                    rawFileStore.write(buf, pos, rlen);
                    pos += rlen;
                    ByteBuffer nioBuffer = ByteBuffer.wrap(buf);
                    nioBuffer.limit(rlen);
                    try {
                        md.update(nioBuffer);
                    } catch (Exception e) {
                        // This better not happen. :)
                        throw new RuntimeException(e);
                    }
                }

                if (md != null)
                {
                    o.setSha1(byteArrayToHexString(md.digest()));
                    iUpdate.saveObject(o);
                }
            }

        } catch (Exception e)
        {
            e.printStackTrace();   
        }

    }

    public void populateSHA1(MessageDigest md, Long id)
    {
        Pixels p = iQuery.get(Pixels.class, id);
        p.setSha1(byteArrayToHexString(md.digest()));
        iUpdate.saveObject(p);
    }


    static String byteArrayToHexString(byte in[]) {

        byte ch = 0x00;
        int i = 0;

        if (in == null || in.length <= 0) {
            return null;
        }

        String pseudo[] = { "0", "1", "2", "3", "4", "5", "6", "7", "8", "9",
                "a", "b", "c", "d", "e", "f" };

        StringBuffer out = new StringBuffer(in.length * 2);

        while (i < in.length) {

            ch = (byte) (in[i] & 0xF0);
            ch = (byte) (ch >>> 4);
            ch = (byte) (ch & 0x0F);
            out.append(pseudo[ch]);
            ch = (byte) (in[i] & 0x0F);
            out.append(pseudo[ch]);
            i++;

        }

        String rslt = new String(out);
        return rslt;
    }
    /**
     * Check the MinMax values stored in the DB and sync them with the new values
     * we generate in the channelMinMax reader, then save them to the DB. 
     * @param id The <code>Pixels</code> id.
     */
    @SuppressWarnings("unchecked")
    public void populateMinMax(Long id, Integer i)
    {
        Pixels p = iQuery.findByQuery(
                "select p from Pixels as p left join fetch p.channels " +
                "where p.id = :id", new Parameters().addId(id));
        for (int j=0; j < p.getSizeC(); j++)
        {
            Channel channel = p.getChannel(j);
            Channel readerChannel = getPixels(i).getChannel(j);
            channel.setStatsInfo(readerChannel.getStatsInfo());
        }
        iUpdate.saveObject(p);
    }

    public IRepositoryInfo getRepositoryInfo()
    {
        return iInfo;
    }

    public long getRepositorySpace()
    {
        return iInfo.getFreeSpaceInKilobytes();
    }

    /**
     * Returns an Image from the internal indexed image list. The indexed image
     * list is extended as required and the Image object itself is created as 
     * required.
     * 
     * @param imageIndex The image index.
     * @return See above.
     */
    public Image getImage(int imageIndex)
    {
        if (imageList.size() < (imageIndex + 1))
        {
            for (int i = imageList.size(); i < imageIndex; i++)
            {
                // Inserting null here so that we don't place potentially bogus
                // Images into the list which will eventually be saved into
                // the database.
                imageList.add(null);
            }
            imageList.add(new Image());
        }

        // We're going to check to see if the image list has a null value and
        // update it as required.
        Image i = imageList.get(imageIndex);
        if (i == null)
        {
            i = new Image();
            imageList.set(imageIndex, i);
        }
        return i;
    }

    /**
     * Returns a Pixels from a given Image's indexed pixels list. The indexed
     * pixels list is extended as required and the Pixels object itself is
     * created as required. This also invalidates the PlaneInfo ordered cache
     * if the pixelsIndex is different than the one currently stored. You 
     * <b>must not</b> attempt to retrieve two different Pixels instances and 
     * expect to have planeIndexes maintained.
     * 
     * @param imageIndex The image index.
     * @param pixelsIndex The pixels index within <code>imageIndex</code>.
     * @return See above.
     */
    public Pixels getPixels(int imageIndex, int pixelsIndex)
    {
        Image image = getImage(imageIndex);

        if (image.sizeOfPixels() < (pixelsIndex + 1))
        {
            for (int i = image.sizeOfPixels(); i <= pixelsIndex; i++)
            {
                // Since OMERO model objects prevent us from inserting nulls
                // here we must insert a Pixels object. We also need to ensure
                // that the OMERO specific "sha1" field is filled.
                Pixels p = new Pixels();
                // FIXME: We *really* should deal with this properly... finally.
                p.setSha1("foo");
                image.addPixels(p);
            }
        }

        Iterator<Pixels> i = image.iteratePixels();
        int j = 0;
        while (i.hasNext())
        {
            Pixels p = i.next();
            if (j == pixelsIndex)
            {
                // This ensures that we can lookup the Pixels set at a later
                // time based upon its "series" in Bio-Formats terms.
                // FIXME: Note that there is no way to really ensure that
                // "series" accurately maps to index in the List.
                if (!pixelsList.contains(p))
                {
                    pixelsList.add(p);
                }
                return p;
            }
            j++;
        }
        throw new RuntimeException(
                "Unable to locate pixels index: " + pixelsIndex);
    }

    /**
     * Returns a Pixels from the internal "series" indexed pixels list. FIXME: 
     * Note that there is no way to really ensure that <code>series</code> 
     * accurately maps to index in the List.
     * @param series The Bio-Formats series to lookup.
     * @return See above.
     */
    public Pixels getPixels(int series)
    {
        return pixelsList.get(series);
    }

    /* ---- Metadata set functions based on the Image Root ---- */

    /* ---- Image ---- */

    /* (non-Javadoc)
     * @see loci.formats.meta.MetadataStore#setImageName(java.lang.String, int)
     */
    public void setImageName(String name, int imageIndex)
    {
        log.debug(String.format(
                "Setting Image[%d] name: '%s'", imageIndex, name));
        Image i = getImage(imageIndex);
        i.setName(name);
    }

    /* (non-Javadoc)
     * @see loci.formats.meta.MetadataStore#setImageCreationDate(java.lang.String, int)
     */
    public void setImageCreationDate(String creationDate, int imageIndex)
    {
        log.debug(String.format(
                "Setting Image[%d] creation date: '%s'", imageIndex, creationDate));

        if (creationDate != null)
        {
            try
            {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss");
                java.util.Date date = sdf.parse(creationDate);
                creationTimestamp = new Timestamp(date.getTime());
            }
            catch (ParseException pe)
            {
                creationTimestamp = null;
            }
        }
    }

    /* (non-Javadoc)
     * @see loci.formats.meta.MetadataStore#setImageDescription(java.lang.String, int)
     */
    public void setImageDescription(String description, int imageIndex)
    {
        if (description != null) 
            description = description.trim();
        log.debug(String.format(
                "Setting Image[%d] description: '%s'", imageIndex, description));
        Image i = getImage(imageIndex);
        i.setDescription(description);
    }

    /* (non-Javadoc)
     * @see loci.formats.meta.MetadataStore#setImageInstrumentRef(java.lang.Integer, int)
     */
    public void setImageInstrumentRef(Integer instrumentRef, int imageIndex)
    {
        Image image = getImage(imageIndex);
        Instrument instrument = getInstrument(instrumentRef);
        log.debug(String.format(
                "Setting ImageInstrumentRef[%d] image: '%d", instrumentRef, imageIndex));
        if (instrument != null)
        {
            log.debug(String.format(
                    " -- skipped till fixed."));
            //image.setSetup(instrument);
        }
    }
    
    /* ---- Pixels ---- */

    /* (non-Javadoc)
     * @see loci.formats.meta.MetadataStore#setPixelsSizeX(java.lang.Integer, int, int)
     */
    public void setPixelsSizeX(Integer sizeX, int imageIndex, int pixelsIndex)
    {
        log.debug(String.format(
                "Setting Image[%d] Pixels[%d] sizeX: '%d'",
                imageIndex, pixelsIndex, sizeX));
        Pixels p = getPixels(imageIndex, pixelsIndex);
        p.setSizeX(sizeX);
    }

    /* (non-Javadoc)
     * @see loci.formats.meta.MetadataStore#setPixelsSizeY(java.lang.Integer, int, int)
     */
    public void setPixelsSizeY(Integer sizeY, int imageIndex, int pixelsIndex)
    {
        log.debug(String.format(
                "Setting Image[%d] Pixels[%d] sizeY: '%d'",
                imageIndex, pixelsIndex, sizeY));
        Pixels p = getPixels(imageIndex, pixelsIndex);
        p.setSizeY(sizeY);
    }

    /* (non-Javadoc)
     * @see loci.formats.meta.MetadataStore#setPixelsSizeZ(java.lang.Integer, int, int)
     */
    public void setPixelsSizeZ(Integer sizeZ, int imageIndex, int pixelsIndex)
    {
        log.debug(String.format(
                "Setting Image[%d] Pixels[%d] sizeZ: '%d'",
                imageIndex, pixelsIndex, sizeZ));
        Pixels p = getPixels(imageIndex, pixelsIndex);
        p.setSizeZ(sizeZ);
    }

    /* (non-Javadoc)
     * @see loci.formats.meta.MetadataStore#setPixelsSizeC(java.lang.Integer, int, int)
     */
    @SuppressWarnings("unchecked")
    public void setPixelsSizeC(Integer sizeC, int imageIndex, int pixelsIndex)
    {
        log.debug(String.format(
                "Setting Image[%d] Pixels[%d] sizeC: '%d'",
                imageIndex, pixelsIndex, sizeC));
        Pixels p = getPixels(imageIndex, pixelsIndex);
        if (p.getSizeC() != null && sizeC != null && p.getSizeC().equals(sizeC))
        {
            log.debug("Not resetting channels.");
            return;
        }
        p.setSizeC(sizeC);
        if (p.sizeOfChannels() != 0)
        {
            p.clearChannels();
        }
        for (int i = 0; i < sizeC; i++)
        {
            Channel c = new Channel();
            c.setLogicalChannel(new LogicalChannel());
            p.addChannel(c);
        }
    }

    /* (non-Javadoc)
     * @see loci.formats.meta.MetadataStore#setPixelsSizeT(java.lang.Integer, int, int)
     */
    public void setPixelsSizeT(Integer sizeT, int imageIndex, int pixelsIndex)
    {
        log.debug(String.format(
                "Setting Image[%d] Pixels[%d] sizeT: '%d'",
                imageIndex, pixelsIndex, sizeT));
        Pixels p = getPixels(imageIndex, pixelsIndex);
        p.setSizeT(sizeT);
    }

    /* (non-Javadoc)
     * @see loci.formats.meta.MetadataStore#setPixelsPixelType(java.lang.String, int, int)
     */
    public void setPixelsPixelType(String pixelType, int imageIndex, int pixelsIndex)
    {
        log.debug(String.format(
                "Setting Image[%d] Pixels[%d] pixel type: '%s'",
                imageIndex, pixelsIndex, pixelType));

        String lcPixelType = pixelType.toLowerCase();
        
        // Retrieve enumerations from the server               
        PixelsType type =
            (PixelsType) getEnumeration(PixelsType.class, lcPixelType);

        Pixels p = getPixels(imageIndex, pixelsIndex);
        p.setPixelsType(type);
    }

    /* (non-Javadoc)
     * @see loci.formats.meta.MetadataStore#setPixelsDimensionOrder(java.lang.String, int, int)
     */
    public void setPixelsDimensionOrder(String dimensionOrder, int imageIndex, int pixelsIndex)
    {
        log.debug(String.format(
                "Setting Image[%d] Pixels[%d] dimension order: '%s'",
                imageIndex, pixelsIndex, dimensionOrder));

        DimensionOrder order = (DimensionOrder) getEnumeration(DimensionOrder.class, dimensionOrder);
        Pixels p = getPixels(imageIndex, pixelsIndex);
        p.setDimensionOrder(order);
    }

    /* ---- PixelsDimensions --- */

    /**
     * Retrieve PixelDimenions for p, or create one if needed
     * @param p
     * @return PixelsDimensions
     */
    private PixelsDimensions getPixelsDimensions (Pixels p)
    {
        PixelsDimensions dims = p.getPixelsDimensions();
        if (dims == null)
        {
            dims = new PixelsDimensions();
            dims.setSizeX(0.0f);
            dims.setSizeY(0.0f);
            dims.setSizeZ(0.0f);
            p.setPixelsDimensions(dims);
        }
        return dims;       
    }

    /* (non-Javadoc)
     * @see loci.formats.meta.MetadataStore#setDimensionsPhysicalSizeX(java.lang.Float, int, int)
     */
    public void setDimensionsPhysicalSizeX(Float physicalSizeX, int imageIndex, int pixelsIndex)
    {
        if (physicalSizeX == null || physicalSizeX <= 0.000001)
        {
            log.warn("physicalSizeZ is <= 0.000001f, setting to 1.0f");
            physicalSizeX = 1.0f;
        } else {
            log.debug(String.format(
                    "Setting Image[%d] Pixels[%d] physical size X: '%f'",
                    imageIndex, pixelsIndex, physicalSizeX));
        }

        Pixels p = getPixels(imageIndex, pixelsIndex);
        PixelsDimensions dims = getPixelsDimensions(p);
        dims.setSizeX(physicalSizeX);
    }

    /* (non-Javadoc)
     * @see loci.formats.meta.MetadataStore#setDimensionsPhysicalSizeY(java.lang.Float, int, int)
     */
    public void setDimensionsPhysicalSizeY(Float physicalSizeY, int imageIndex, int pixelsIndex)
    {
        if (physicalSizeY == null || physicalSizeY <= 0.000001)
        {
            log.warn("physicalSizeZ is <= 0.000001f, setting to 1.0f");
            physicalSizeY = 1.0f;
        } else {
            log.debug(String.format(
                    "Setting Image[%d] Pixels[%d] physical size Y: '%f'",
                    imageIndex, pixelsIndex, physicalSizeY));
        }

        Pixels p = getPixels(imageIndex, pixelsIndex);
        PixelsDimensions dims = getPixelsDimensions(p);
        dims.setSizeY(physicalSizeY);
    }

    /* (non-Javadoc)
     * @see loci.formats.meta.MetadataStore#setDimensionsPhysicalSizeZ(java.lang.Float, int, int)
     */
    public void setDimensionsPhysicalSizeZ(Float physicalSizeZ, int imageIndex, int pixelsIndex)
    {
        if (physicalSizeZ == null || physicalSizeZ <= 0.000001)
        {
            log.warn("physicalSizeZ is <= 0.000001f, setting to 1.0f");
            physicalSizeZ = 1.0f;
        } else {
            log.debug(String.format(
                    "Setting Image[%d] Pixels[%d] physical size Z: '%f'",
                    imageIndex, pixelsIndex, physicalSizeZ));
        }

        Pixels p = getPixels(imageIndex, pixelsIndex);
        PixelsDimensions dims = getPixelsDimensions(p);
        dims.setSizeZ(physicalSizeZ);
    }

    /* ---- Imaging Environment (OMERO Image.Condition) ---- */    

    /**
     * Get ImagingEnvironment, creating one if it doesn't exist
     * @param imageIndex
     * @return ImagingEnvironment
     */
    private ImagingEnvironment getImagingEnvironment (int imageIndex)
    {
        Image i = getImage(imageIndex);
        ImagingEnvironment ie = i.getCondition();
        if (ie == null)
        {
            ie = new ImagingEnvironment();
            i.setCondition(ie);
        }
        return ie;       
    }

    public void setImagingEnvironmentTemperature(Float temperature, int imageIndex) {
        ImagingEnvironment ie = getImagingEnvironment(imageIndex);
        ie.setTemperature(temperature);
    }

    public void setImagingEnvironmentAirPressure(Float airPressure, int imageIndex) {
        ImagingEnvironment ie = getImagingEnvironment(imageIndex);
        ie.setAirPressure(airPressure);
    }

    public void setImagingEnvironmentHumidity(Float humidity, int imageIndex) {
        ImagingEnvironment ie = getImagingEnvironment(imageIndex);
        ie.setHumidity(humidity);
    }

    public void setImagingEnvironmentCO2Percent(Float percent, int imageIndex) {
        ImagingEnvironment ie = getImagingEnvironment(imageIndex);
        ie.setCo2percent(percent);
    }

    /* ---- PlaneInfo ---- */         

    /**
     * Returns a PlaneInfo from a given Image's, Pixels' indexed  plane info
     * list. The indexed plane info list is extended as required and the 
     * PlaneInfo object itself is created as required.
     * 
     * @param imageIndex The image index.
     * @param pixelsIndex The pixels index within <code>imageIndex</code>.
     * @param planeIndex The plane info index within <code>pixelsIndex</code>.
     * @return See above.
     */
    public PlaneInfo getPlaneInfo(int imageIndex, int pixelsIndex,
            int planeIndex)
    {
        Pixels pixels = getPixels(imageIndex, pixelsIndex);
        if (!planeInfoCache.containsKey(pixels))
        {
            planeInfoCache.put(pixels, new ArrayList<PlaneInfo>());
        }

        List<PlaneInfo> cache = planeInfoCache.get(pixels);
        if (cache.size() < (planeIndex + 1))
        {
            for (int i = cache.size(); i <= planeIndex; i++)
            {
                // Since OMERO model objects prevent us from inserting nulls
                // here we must insert a PlaneInfo object. Also, we need an
                // ordered list of PlaneInfo objects for later reference so
                // we're populating the cache here which will be invalidated
                // upon a call to createRoot().
                PlaneInfo info = new PlaneInfo();
                // FIXME: Time stamp needs fixing.
                info.setTimestamp(0.0f);
                cache.add(info);
                pixels.addPlaneInfo(info);
            }
        }
        return cache.get(planeIndex);
    }

    /* (non-Javadoc)
     * @see loci.formats.meta.MetadataStore#setPlaneTheZ(java.lang.Integer, int, int, int)
     */
    public void setPlaneTheZ(Integer theZ, int imageIndex, int pixelsIndex,
            int planeIndex)
    {
        log.debug(String.format(
                "Setting Image[%d] Pixels[%d] PlaneInfo[%d] theZ: '%d'",
                imageIndex, pixelsIndex, planeIndex, theZ));
        PlaneInfo p = getPlaneInfo(imageIndex, pixelsIndex, planeIndex);
        p.setTheZ(theZ);
    }

    /* (non-Javadoc)
     * @see loci.formats.meta.MetadataStore#setPlaneTheC(java.lang.Integer, int, int, int)
     */
    public void setPlaneTheC(Integer theC, int imageIndex, int pixelsIndex,
            int planeIndex)
    {
        log.debug(String.format(
                "Setting Image[%d] Pixels[%d] PlaneInfo[%d] theC: '%d'",
                imageIndex, pixelsIndex, planeIndex, theC));
        PlaneInfo p = getPlaneInfo(imageIndex, pixelsIndex, planeIndex);
        p.setTheC(theC);
    }

    /* (non-Javadoc)
     * @see loci.formats.meta.MetadataStore#setPlaneTheT(java.lang.Integer, int, int, int)
     */
    public void setPlaneTheT(Integer theT, int imageIndex, int pixelsIndex,
            int planeIndex)
    {
        log.debug(String.format(
                "Setting Image[%d] Pixels[%d] PlaneInfo[%d] theT: '%d'",
                imageIndex, pixelsIndex, planeIndex, theT));
        PlaneInfo p = getPlaneInfo(imageIndex, pixelsIndex, planeIndex);
        p.setTheT(theT);
    }

    /* (non-Javadoc)
     * @see loci.formats.meta.MetadataStore#setPlaneTimingDeltaT(java.lang.Float, int, int, int)
     */
    public void setPlaneTimingDeltaT(Float deltaT, int imageIndex,
            int pixelsIndex, int planeIndex)
    {
        log.debug(String.format(
                "Setting Image[%d] Pixels[%d] PlaneInfo[%d] deltaT: '%f'",
                imageIndex, pixelsIndex, planeIndex, deltaT));
        PlaneInfo p = getPlaneInfo(imageIndex, pixelsIndex, planeIndex);
        p.setTimestamp(deltaT);
    }

    /* (non-Javadoc)
     * @see loci.formats.meta.MetadataStore#setPlaneTimingExposureTime(java.lang.Float, int, int, int)
     */
    public void setPlaneTimingExposureTime(Float exposureTime, int imageIndex,
            int pixelsIndex, int planeIndex)
    {
        log.debug(String.format(
                "Setting Image[%d] Pixels[%d] PlaneInfo[%d] exposure time: '%f'",
                imageIndex, pixelsIndex, planeIndex, exposureTime));
        PlaneInfo p = getPlaneInfo(imageIndex, pixelsIndex, planeIndex);
        p.setExposureTime(exposureTime);
    }

    /* ---- LogicalChannels ---- */

    /* (non-Javadoc)
     * @see loci.formats.meta.MetadataStore#setLogicalChannelName(java.lang.String, int, int)
     */
    public void setLogicalChannelName(String name, int imageIndex,
            int logicalChannelIndex)
    {
        Image image = getImage(imageIndex);
        Iterator<Pixels> i = image.iteratePixels();
        while (i.hasNext())
        {
            Pixels p = i.next();
            log.debug(String.format(
                    "Setting Image[%d] Pixels[%s] LogicalChannel[%d] name: '%s'",
                    imageIndex, p, logicalChannelIndex, name));
            LogicalChannel lc = p.getChannel(logicalChannelIndex).getLogicalChannel();
            lc.setName(name);
        }
    }

    /* (non-Javadoc)
     * @see loci.formats.meta.MetadataStore#setLogicalChannelIlluminationType(java.lang.String, int, int)
     */
    public void setLogicalChannelIlluminationType(String illuminationType,
            int imageIndex, int logicalChannelIndex)
    {
        Image image = getImage(imageIndex);
        Iterator<Pixels> i = image.iteratePixels();
        while (i.hasNext())
        {
            Pixels p = i.next();
            log.debug(String.format(
                    "Setting Image[%d] Pixels[%s] LogicalChannel[%d] illumination type: '%s'",
                    imageIndex, p, logicalChannelIndex, illuminationType));
            LogicalChannel lc = p.getChannel(logicalChannelIndex).getLogicalChannel();
            Illumination iType = (Illumination) getEnumeration(AcquisitionMode.class, illuminationType);
            lc.setIllumination(iType);
        }
    }

    /* (non-Javadoc)
     * @see loci.formats.meta.MetadataStore#setLogicalChannelPinholeSize(java.lang.Integer, int, int)
     */
    public void setLogicalChannelPinholeSize(Integer pinholeSize,
            int imageIndex, int logicalChannelIndex)
    {
        Image image = getImage(imageIndex);
        Iterator<Pixels> i = image.iteratePixels();
        while (i.hasNext())
        {
            Pixels p = i.next();
            log.debug(String.format(
                    "Setting Image[%d] Pixels[%s] LogicalChannel[%d] pinhole size: '%d'",
                    imageIndex, p, logicalChannelIndex, pinholeSize));
            LogicalChannel lc = 
                p.getChannel(logicalChannelIndex).getLogicalChannel();
            lc.setPinHoleSize(pinholeSize);
        }
    }

    /* (non-Javadoc)
     * @see loci.formats.meta.MetadataStore#setLogicalChannelPhotometricInterpretation(java.lang.String, int, int)
     */
    public void setLogicalChannelPhotometricInterpretation(
            String photometricInterpretation, int imageIndex,
            int logicalChannelIndex)
    {
        Image image = getImage(imageIndex);
        Iterator<Pixels> i = image.iteratePixels();
        while (i.hasNext())
        {
            Pixels p = i.next();
            log.debug(String.format(
                    "Setting Image[%d] Pixels[%s] LogicalChannel[%d] photometric interpretation: '%s'",
                    imageIndex, p, logicalChannelIndex, photometricInterpretation));
            LogicalChannel lc = 
                p.getChannel(logicalChannelIndex).getLogicalChannel();
            PhotometricInterpretation pi = 
                (PhotometricInterpretation) getEnumeration(
                        PhotometricInterpretation.class, photometricInterpretation);
            lc.setPhotometricInterpretation(pi);
        }
    }

    /* (non-Javadoc)
     * @see loci.formats.meta.MetadataStore#setLogicalChannelMode(java.lang.String, int, int)
     */
    public void setLogicalChannelMode(String mode, int imageIndex,
            int logicalChannelIndex)
    {
        Image image = getImage(imageIndex);
        Iterator<Pixels> i = image.iteratePixels();
        while (i.hasNext())
        {
            Pixels p = i.next();
            log.debug(String.format(
                    "Setting Image[%d] Pixels[%s] LogicalChannel[%d] channel mode: '%s'",
                    imageIndex, p, logicalChannelIndex, mode));
            LogicalChannel lc = 
                p.getChannel(logicalChannelIndex).getLogicalChannel();
            AcquisitionMode m = 
                (AcquisitionMode) getEnumeration(AcquisitionMode.class, mode);
            lc.setMode(m);
        }
    }

    /* (non-Javadoc)
     * @see loci.formats.meta.MetadataStore#setLogicalChannelContrastMethod(java.lang.String, int, int)
     */
    public void setLogicalChannelContrastMethod(String contrastMethod,
            int imageIndex, int logicalChannelIndex)
    {
        Image image = getImage(imageIndex);
        Iterator<Pixels> i = image.iteratePixels();
        while (i.hasNext())
        {
            Pixels p = i.next();
            log.debug(String.format(
                    "Setting Image[%d] Pixels[%s] LogicalChannel[%d] contrast method: '%s'",
                    imageIndex, p, logicalChannelIndex, contrastMethod));
            LogicalChannel lc = p.getChannel(logicalChannelIndex).getLogicalChannel();
            ContrastMethod m = (ContrastMethod) 
            getEnumeration(ContrastMethod.class, contrastMethod);
            lc.setContrastMethod(m);
        }
    }

    /* (non-Javadoc)
     * @see loci.formats.meta.MetadataStore#setLogicalChannelExWave(java.lang.Integer, int, int)
     */
    public void setLogicalChannelExWave(Integer exWave, int imageIndex,
            int logicalChannelIndex)
    {
        Image image = getImage(imageIndex);
        Iterator<Pixels> i = image.iteratePixels();
        while (i.hasNext())
        {
            Pixels p = i.next();
            log.debug(String.format(
                    "Setting Image[%d] Pixels[%s] LogicalChannel[%d] excitation wavelength: '%d'",
                    imageIndex, p, logicalChannelIndex, exWave));
            LogicalChannel lc = 
                p.getChannel(logicalChannelIndex).getLogicalChannel();
            lc.setExcitationWave(exWave);
            if (lc.getPhotometricInterpretation() == null)
            {
                log.debug("Setting Photometric iterpretation to monochrome");
                PhotometricInterpretation pi = (PhotometricInterpretation) 
                getEnumeration(PhotometricInterpretation.class, "Monochrome");
                lc.setPhotometricInterpretation(pi);
            }
        }
    }

    /* (non-Javadoc)
     * @see loci.formats.meta.MetadataStore#setLogicalChannelEmWave(java.lang.Integer, int, int)
     */
    public void setLogicalChannelEmWave(Integer emWave, int imageIndex,
            int logicalChannelIndex)
    {
        Image image = getImage(imageIndex);
        Iterator<Pixels> i = image.iteratePixels();
        while (i.hasNext())
        {
            Pixels p = i.next();
            log.debug(String.format(
                    "Setting Image[%d] Pixels[%s] LogicalChannel[%d] emission wavelength: '%d'",
                    imageIndex, p, logicalChannelIndex, emWave));
            LogicalChannel lc = p.getChannel(logicalChannelIndex).getLogicalChannel();
            lc.setEmissionWave(emWave);
            if (lc.getPhotometricInterpretation() == null)
            {
                log.debug("Setting Photometric iterpretation to monochrome");
                PhotometricInterpretation pi = (PhotometricInterpretation) 
                getEnumeration(PhotometricInterpretation.class, "Monochrome");
                lc.setPhotometricInterpretation(pi);
            }
        }
    }

    /* (non-Javadoc)
     * @see loci.formats.meta.MetadataStore#setLogicalChannelFluor(java.lang.String, int, int)
     */
    public void setLogicalChannelFluor(String fluor, int imageIndex,
            int logicalChannelIndex)
    {
        Image image = getImage(imageIndex);
        Iterator<Pixels> i = image.iteratePixels();
        while (i.hasNext())
        {
            Pixels p = i.next();
            log.debug(String.format(
                    "Setting Image[%d] Pixels[%s] LogicalChannel[%d] fluor: '%s'",
                    imageIndex, p, logicalChannelIndex, fluor));
            LogicalChannel lc = p.getChannel(logicalChannelIndex).getLogicalChannel();
            lc.setFluor(fluor);
        }
    }

    /* (non-Javadoc)
     * @see loci.formats.meta.MetadataStore#setLogicalChannelNdFilter(java.lang.Float, int, int)
     */
    @SuppressWarnings("unchecked")
    public void setLogicalChannelNdFilter(Float ndFilter, int imageIndex,
            int logicalChannelIndex)
    {
        Image image = getImage(imageIndex);
        Iterator<Pixels> i = image.iteratePixels();
        while (i.hasNext())
        {
            Pixels p = i.next();
            log.debug(String.format(
                    "Setting Image[%d] Pixels[%s] LogicalChannel[%d] ndFilter: '%f'",
                    imageIndex, p, logicalChannelIndex, ndFilter));
            LogicalChannel lc = p.getChannel(logicalChannelIndex).getLogicalChannel();
            lc.setNdFilter(ndFilter);
        }
    }

    /* (non-Javadoc)
     * @see loci.formats.meta.MetadataStore#setLogicalChannelPockelCellSetting(java.lang.Integer, int, int)
     */
    @SuppressWarnings("unchecked")
    public void setLogicalChannelPockelCellSetting(Integer pockelCellSetting,
            int imageIndex, int logicalChannelIndex)
    {
        Image image = getImage(imageIndex);
        Iterator<Pixels> i = image.iteratePixels();
        while (i.hasNext())
        {
            Pixels p = i.next();
            log.debug(String.format(
                    "Setting Image[%d] Pixels[%s] LogicalChannel[%d] " +
                    "pockel cell setting: '%d'",
                    imageIndex, p, logicalChannelIndex, pockelCellSetting));
            LogicalChannel lc = 
                p.getChannel(logicalChannelIndex).getLogicalChannel();
            lc.setPockelCellSetting(pockelCellSetting);
            // FIXME: Should pockel cell be String or Integer?
        }
    }

    /* --- Stage Position --- */

    /* (non-Javadoc)
     * @see loci.formats.meta.MetadataStore#setStagePositionPositionX(java.lang.Float, int, int, int)
     */
    public void setStagePositionPositionX(Float positionX, int imageIndex,
            int pixelsIndex, int planeIndex)
    {
        log.debug(String.format(
                "Setting Image[%d] Pixels[%d] PlaneInfo[%d] position X: '%f'",
                imageIndex, pixelsIndex, planeIndex, positionX));
        PlaneInfo p = getPlaneInfo(imageIndex, pixelsIndex, planeIndex);
        p.setPositionX(positionX);
    }

    /* (non-Javadoc)
     * @see loci.formats.meta.MetadataStore#setStagePositionPositionY(java.lang.Float, int, int, int)
     */
    public void setStagePositionPositionY(Float positionY, int imageIndex,
            int pixelsIndex, int planeIndex)
    {
        log.debug(String.format(
                "Setting Image[%d] Pixels[%d] PlaneInfo[%d] position Y: '%f'",
                imageIndex, pixelsIndex, planeIndex, positionY));
        PlaneInfo p = getPlaneInfo(imageIndex, pixelsIndex, planeIndex);
        p.setPositionY(positionY);
    }

    /* (non-Javadoc)
     * @see loci.formats.meta.MetadataStore#setStagePositionPositionZ(java.lang.Float, int, int, int)
     */
    public void setStagePositionPositionZ(Float positionZ, int imageIndex,
            int pixelsIndex, int planeIndex)
    {
        log.debug(String.format(
                "Setting Image[%d] Pixels[%d] PlaneInfo[%d] position Z: '%f'",
                imageIndex, pixelsIndex, planeIndex, positionZ));
        PlaneInfo p = getPlaneInfo(imageIndex, pixelsIndex, planeIndex);
        p.setPositionZ(positionZ);
    }

    /* ---- Stage Label ---- */

    /**
     * Get ImagingEnvironment, creating one if it doesn't exist
     * @param imageIndex
     * @return ImagingEnvironment
     */
    private StageLabel getStageLabel (int imageIndex)
    {
        Image i = getImage(imageIndex);
        StageLabel sl = i.getPosition();
        if (sl == null)
        {
            sl = new StageLabel();
            i.setPosition(sl);
        }
        return sl;       
    }

    public void setStageLabelName(String name, int imageIndex) {
        log.debug(String.format(
                "Setting setStageName[%s] Image[%d]", name, imageIndex));
        if (name == null)
        {
            log.debug(String.format("Stage label cannot be null, setting to 'ome'."));
            name = "ome";
        }
        StageLabel sl = getStageLabel(imageIndex);
        sl.setName(name);
    }

    public void setStageLabelX(Float x, int imageIndex) {
        log.debug(String.format(
                "Setting setStageLabelX[%f] Image[%d]", x, imageIndex));
        if (x == null)
        {
            log.debug(String.format("Stage label X cannot be null, setting to 0.0f."));
            x = 0.0f;
        }
        StageLabel sl = getStageLabel(imageIndex);
        sl.setPositionX(x);
    }

    public void setStageLabelY(Float y, int imageIndex) {
        log.debug(String.format(
                "Setting setStageLabelY[%f] Image[%d]", y, imageIndex));
        if (y == null)
        {
            log.debug(String.format("Stage label Y cannot be null, setting to 0.0f."));
            y = 0.0f;
        }
        StageLabel sl = getStageLabel(imageIndex);
        sl.setPositionY(y); 
    }

    public void setStageLabelZ(Float z, int imageIndex) {
        log.debug(String.format(
                "Setting setStageLabelZ[%f] Image[%d]", z, imageIndex));
        if (z == null)
        {
            log.debug(String.format("Stage label Z cannot be null, setting to 0.0f."));
            z = 0.0f;
        }
        StageLabel sl = getStageLabel(imageIndex);
        sl.setPositionZ(z);   
    }

    /* ---- Detector Settings ---- */

    public void setDetectorSettingsDetector(String detector, int imageIndex,
            int logicalChannelIndex)
    {
        log.debug(String.format(
                "Ignoring setExperimenterID[%s] imageIndex[%d] logicalChannelIndex[%d]",
                detector, imageIndex, logicalChannelIndex));
    }

    public void setDetectorSettingsGain(Float gain, int imageIndex,
            int logicalChannelIndex) {       
        Image image = getImage(imageIndex);
        Iterator<Pixels> i = image.iteratePixels();
        while (i.hasNext())
        {
            Pixels p = i.next();
            log.debug(String.format(
                    "Setting Image[%d] Pixels[%s] LogicalChannel[%d] " +
                    "Detector Settings Gain: '%f'",
                    imageIndex, p, logicalChannelIndex, gain));
            DetectorSettings ds = p.getChannel(logicalChannelIndex).getLogicalChannel().getDetectorSettings();
            ds.setGain(gain);
        }
    }

    public void setDetectorSettingsOffset(Float offset, int imageIndex,
            int logicalChannelIndex) {
        Image image = getImage(imageIndex);
        Iterator<Pixels> i = image.iteratePixels();
        while (i.hasNext())
        {
            Pixels p = i.next();
            log.debug(String.format(
                    "Setting Image[%d] Pixels[%s] LogicalChannel[%d] " +
                    "Detector Settings Offset: '%f'",
                    imageIndex, p, logicalChannelIndex, offset));
            DetectorSettings ds = p.getChannel(logicalChannelIndex).getLogicalChannel().getDetectorSettings();
            ds.setOffsetValue(offset);
        }
    }

    /* ---- Instrument-based Methods ---- */

    /**
     * Returns an Instrument from the internal indexed instrument list. The indexed 
     * instrument list is extended as required and the Instrument object itself is 
     * created as required.
     * 
     * @param instrumentIndex The instrument index.
     * @return See above.
     */
    public Instrument getInstrument(int instrumentIndex)
    {
        if (instrumentList.size() < (instrumentIndex + 1))
        {
            for (int i = instrumentList.size(); i < instrumentIndex; i++)
            {
                // Inserting null here so that we don't place potentially bogus
                // Instrument into the list which will eventually be saved into
                // the database.
                instrumentList.add(null);
            }
            instrumentList.add(new Instrument());
        }

        // We're going to check to see if the instrument list has a null value and
        // update it as required.
        Instrument i = instrumentList.get(instrumentIndex);
        if (i == null)
        {
            i = new Instrument();
            instrumentList.set(instrumentIndex, i);
        }
        return i;
    }

    /* ---- Light Source Settings ---- */

    /* Assuming we're working with Light Sources now */
    public void setLightSourceID(String id, int instrumentIndex,
            int lightSourceIndex)
    {
        // Add this lsid to the lsidMap and set currentID = id.
        mapLSID(id);
        
        log.debug(String.format(
                "Mapping LightSourceID[%s] InstrumentIndex[%d] lightSourceIndex[%d]",
                id, instrumentIndex, lightSourceIndex));    
    }
 
    /* Based on the currentLSID we have stored, see if the lightsource is set, if not, set it */
    private LightSource getLightSource(Instrument instrument, int lightSourceIndex)
    {
        
        if ((instrument.sizeOfLightSource() - 1) < lightSourceIndex)
        {
            MetaLightSource mls = new MetaLightSource();
            lsidMap.put(currentLSID, mls);
            instrument.addLightSource(mls);
        }
        
        Iterator<LightSource> i = instrument.iterateLightSource();
        int j = 0;

        while (i.hasNext())
        {
            if (j == lightSourceIndex)
            {
                LightSource ls = (LightSource) i.next();
                return ls;
            }
            i.next();
        }
        return null;
    }

    public void setLightSourceSettingsLightSource(String lightSource,
            int imageIndex, int logicalChannelIndex)
    {
        Image image = getImage(imageIndex);
        Iterator<Pixels> i = image.iteratePixels();
        while (i.hasNext())
        {
            Pixels p = i.next();
            log.debug(String.format(
                    "Setting Image[%d] Pixels[%s] LogicalChannel[%d] setLightSourceSettingsLightSource: '%s'",
                    imageIndex, p, logicalChannelIndex, lightSource));
            //LogicalChannel lc = p.getChannel(logicalChannelIndex).getLogicalChannel();

        }
    }

    public void setLightSourceSettingsAttenuation(Float attenuation,
            int imageIndex, int logicalChannelIndex) {
        log.debug(String.format(
                "TODO: setLightSourceSettingsAttenuation[%f] imageIndex[%d] logicalChannelIndex[%d] ",
                attenuation, imageIndex, logicalChannelIndex));  
    }

    public void setLightSourceSettingsWavelength(Integer wavelength,
            int imageIndex, int logicalChannelIndex) {
        log.debug(String.format(
                "TODO: setLightSourceSettingsWavelength[%d] imageIndex[%d] logicalChannelIndex[%d] ",
                wavelength, imageIndex, logicalChannelIndex));  
    }

    /* ---- Light Source ---- */

    public void setLightSourceManufacturer(String manufacturer, int instrumentIndex, int lightSourceIndex) 
    {
        log.debug(String.format(
                "setLightSourceManufacturer[%s] instrumentIndex[%d] lightSourceIndex[%d]",
                manufacturer, instrumentIndex, lightSourceIndex));
        
        Instrument instrument = getInstrument(instrumentIndex);
        LightSource ls = getLightSource(instrument, lightSourceIndex);            
        if (ls != null)
            ls.setManufacturer(manufacturer);
    }

    public void setLightSourceModel(String model, int instrumentIndex, int lightSourceIndex) 
    {
        log.debug(String.format(
                "setLightSourceModel[%s] instrumentIndex[%d] lightSourceIndex[%d]",
                model, instrumentIndex, lightSourceIndex));
        
        Instrument instrument = getInstrument(instrumentIndex);
        LightSource ls = getLightSource(instrument, lightSourceIndex);            
        if (ls != null)
            ls.setModel(model);
    }

    public void setLightSourceSerialNumber(String serialNumber, int instrumentIndex, int lightSourceIndex) 
    {
        log.debug(String.format(
                "setLightSourceSerialNumber[%s] instrumentIndex[%d] lightSourceIndex[%d]",
                serialNumber, instrumentIndex, lightSourceIndex));
        
        Instrument instrument = getInstrument(instrumentIndex);
        LightSource ls = getLightSource(instrument, lightSourceIndex);            
        if (ls != null)
            ls.setSerialNumber(serialNumber);
    }

    public void setLightSourcePower(Float power, int instrumentIndex,
            int lightSourceIndex)
    {
        log.debug(String.format(
                "setLightSourcePower[%f] instrumentIndex[%d] lightSourceIndex[%d]",
                power, instrumentIndex, lightSourceIndex));
        
        Instrument instrument = getInstrument(instrumentIndex);
        LightSource ls = getLightSource(instrument, lightSourceIndex);            
        if (ls != null)
            ls.setPower(power);
    }

    /* ---- Laser ---- */ 

    public Laser getLaser(Instrument instrument, int lightSourceIndex)
    {
        if ((instrument.sizeOfLightSource() - 1) < lightSourceIndex)
        {
            Laser laser = new Laser();
            lsidMap.put(currentLSID, laser);
            instrument.addLightSource(laser);
        } else 
        {
            if (lsidMap.get(currentLSID) instanceof MetaLightSource)
            {
                Laser laser = new Laser();
                
                MetaLightSource mls = (MetaLightSource) lsidMap.get(currentLSID);
                
                mls.copyData(laser);
                
                instrument.removeLightSource(mls);
                
                instrument.addLightSource(laser);
                
            }
        }  
        
        LightSource ls = getLightSource(instrument, lightSourceIndex);
        if (ls instanceof Laser)
        {
            return (Laser) ls; 
        }  
        return null;  
    }
    
    public void setLaserFrequencyMultiplication(
            Integer frequencyMultiplication, int instrumentIndex, int lightSourceIndex) 
    {
        log.debug(String.format(
                "setLaserFrequencyMultiplication[%d] instrumentIndex[%d] lightSourceIndex[%d]",
                frequencyMultiplication, instrumentIndex, lightSourceIndex));
        
        Instrument instrument = getInstrument(instrumentIndex);
        Laser laser = getLaser(instrument, lightSourceIndex);  
        if (frequencyMultiplication != null && laser != null)
        {
            laser.setFrequencyMultiplication((FrequencyMultiplication)
                    getEnumeration(FrequencyMultiplication.class, frequencyMultiplication + "x"));
        } else if (laser != null) {
            laser.setFrequencyMultiplication(null);        
        }
    }

    public void setLaserLaserMedium(String laserMedium, int instrumentIndex, int lightSourceIndex) 
    {
        log.debug(String.format(
                "setLaserLaserMedium[%s] instrumentIndex[%d] lightSourceIndex[%d]",
                laserMedium, instrumentIndex, lightSourceIndex));
        
        Instrument instrument = getInstrument(instrumentIndex);
        Laser laser = getLaser(instrument, lightSourceIndex);            
        if (laser != null)
            laser.setLaserMedium((LaserMedium) getEnumeration(LaserMedium.class, laserMedium));   
    }

    public void setLaserPower(Float power, int instrumentIndex, int lightSourceIndex) 
    {
        log.debug(String.format(
                "setLaserPower[%f] instrumentIndex[%d] lightSourceIndex[%d]",
                power, instrumentIndex, lightSourceIndex));
        
        Instrument instrument = getInstrument(instrumentIndex);
        Laser laser = getLaser(instrument, lightSourceIndex);            
        if (laser != null)
            laser.setPower(power);
    }

    public void setLaserPulse(String pulse, int instrumentIndex, int lightSourceIndex) 
    {
        log.debug(String.format(
                "setLaserPulse[%s] instrumentIndex[%d] lightSourceIndex[%d]",
                pulse, instrumentIndex, lightSourceIndex));  
        
        Instrument instrument = getInstrument(instrumentIndex);
        Laser laser = getLaser(instrument, lightSourceIndex);            
        if (laser != null)
            laser.setPulse((Pulse) getEnumeration(Pulse.class, pulse));   
    }

    public void setLaserTuneable(Boolean tuneable, int instrumentIndex, int lightSourceIndex) 
    {
        log.debug(String.format(
                "setLaserTuneable[%b] instrumentIndex[%d] lightSourceIndex[%d]",
                tuneable, instrumentIndex, lightSourceIndex));   
        
        Instrument instrument = getInstrument(instrumentIndex);
        Laser laser = getLaser(instrument, lightSourceIndex);            
        if (laser != null)
            laser.setTunable(tuneable);   
    }

    public void setLaserType(String type, int instrumentIndex, int lightSourceIndex) 
    {
        log.debug(String.format(
                "setLaserType[%s] instrumentIndex[%d] lightSourceIndex[%d]",
                type, instrumentIndex, lightSourceIndex));
        
        Instrument instrument = getInstrument(instrumentIndex);
        Laser laser = getLaser(instrument, lightSourceIndex);            
        if (laser != null)
            laser.setType((LaserType) getEnumeration(LaserType.class, type)); 
    }

    public void setLaserWavelength(Integer wavelength, int instrumentIndex, int lightSourceIndex) 
    {
        log.debug(String.format(
                "setLaserWavelength[%d] instrumentIndex[%d] lightSourceIndex[%d]",
                wavelength, instrumentIndex, lightSourceIndex));
        
        Instrument instrument = getInstrument(instrumentIndex);
        Laser laser = getLaser(instrument, lightSourceIndex); 
        if (laser != null)
            laser.setWavelength(wavelength);    
    }

    /* ---- Arc ---- */

    public Arc getArc(Instrument instrument, int lightSourceIndex)
    {
        if ((instrument.sizeOfLightSource() - 1) < lightSourceIndex)
        {
            Arc arc = new Arc();
            lsidMap.put(currentLSID, arc);
            instrument.addLightSource(arc);
        } else 
        {
            if (lsidMap.get(currentLSID) instanceof MetaLightSource)
            {
                Arc arc = new Arc();
                
                MetaLightSource mls = (MetaLightSource) lsidMap.get(currentLSID);
                
                mls.copyData(arc);
                
                instrument.removeLightSource(mls);
                instrument.addLightSource(arc); 
            }
        }
        
        LightSource ls = getLightSource(instrument, lightSourceIndex);
        if (getLightSource(instrument, lightSourceIndex) instanceof Arc)
        {
            return (Arc) ls;
        }  
        return null;
    }
    
    public void setArcPower(Float power, int instrumentIndex, int lightSourceIndex) 
    {
        log.debug(String.format(
                "setArcPower[%f] instrumentIndex[%d] lightSourceIndex[%d]",
                power, instrumentIndex, lightSourceIndex));
        
        Instrument instrument = getInstrument(instrumentIndex);
        Arc arc = getArc(instrument, lightSourceIndex); 
        if (arc != null)
            arc.setPower(power);
    }

    public void setArcType(String type, int instrumentIndex, int lightSourceIndex) 
    {
        log.debug(String.format(
                "setArcType[%s] instrumentIndex[%d] lightSourceIndex[%d]",
                type, instrumentIndex, lightSourceIndex));
        
        Instrument instrument = getInstrument(instrumentIndex);
        Arc arc = getArc(instrument, lightSourceIndex);    
        if (arc != null)
            arc.setType((ArcType) getEnumeration(ArcType.class, type));
    }


    /* ---- Filament ---- */

    public Filament getFilament(Instrument instrument, int lightSourceIndex)
    {
        if ((instrument.sizeOfLightSource() - 1) < lightSourceIndex)
        {
            Filament filament = new Filament();
            lsidMap.put(currentLSID, filament);
            instrument.addLightSource(filament);
        } else 
        {
            if (lsidMap.get(currentLSID) instanceof MetaLightSource)
            {
                Filament filament = new Filament();
                MetaLightSource mls = (MetaLightSource) lsidMap.get(currentLSID);
                
                mls.copyData(filament);
                
                instrument.removeLightSource(mls);
                instrument.addLightSource(filament); 
            }
        }
        
        LightSource ls = getLightSource(instrument, lightSourceIndex);
        if (getLightSource(instrument, lightSourceIndex) instanceof Filament)
        {
            return (Filament) ls;
        }  
        return null;
    }
    
    public void setFilamentPower(Float power, int instrumentIndex,
            int lightSourceIndex) {
        log.debug(String.format(
                "setFilamentPower[%f] instrumentIndex[%d] lightSourceIndex[%d]",
                power, instrumentIndex, lightSourceIndex));
        
        Instrument instrument = getInstrument(instrumentIndex);
        Filament filament = getFilament(instrument, lightSourceIndex); 
        if (filament != null)
            filament.setPower(power);
    }

    public void setFilamentType(String type, int instrumentIndex,
            int lightSourceIndex) {
        log.debug(String.format(
                "setFilamentType[%s] instrumentIndex[%d] lightSourceIndex[%d]",
                type, instrumentIndex, lightSourceIndex));
        
        Instrument instrument = getInstrument(instrumentIndex);
        Filament filament = getFilament(instrument, lightSourceIndex); 
        if (filament != null)
            filament.setType((FilamentType) getEnumeration(FilamentType.class, type)); 
    }
    
    /* ---- Detector ---- */


    public void setDetectorID(String id, int instrumentIndex, int detectorIndex)
    {
        // Add this lsid to the lsidMap and set currentID = id.
        mapLSID(id);
        
        log.debug(String.format(
                "Mapping DetectorID[%s] InstrumentIndex[%d] detectorIndex[%d]",
                id, instrumentIndex, detectorIndex));    
    }

    private Detector getDetector(Instrument instrument, int detectorIndex)
    {
        if ((instrument.sizeOfDetector() - 1) < detectorIndex)
        {
            Detector detector = new Detector();
            lsidMap.put(currentLSID, detector);
            instrument.addDetector(detector);
        } 

        return (Detector) lsidMap.get(currentLSID);
    }
    
    public void setDetectorGain(Float gain, int instrumentIndex,
            int detectorIndex) {
        log.debug(String.format(
                "setDetectorGain[%f] instrumentIndex[%d] detectorIndex[%d]",
                gain, instrumentIndex, detectorIndex));
        
        Instrument instrument = getInstrument(instrumentIndex);
        Detector detector = getDetector(instrument, detectorIndex); 
        if (detector != null)
            detector.setGain(gain);
    }

    public void setDetectorManufacturer(String manufacturer,
            int instrumentIndex, int detectorIndex) {
        log.debug(String.format(
                "setDetectorManufacturer[%s] instrumentIndex[%d] detectorIndex[%d]",
                manufacturer, instrumentIndex, detectorIndex));
        
        Instrument instrument = getInstrument(instrumentIndex);
        Detector detector = getDetector(instrument, detectorIndex); 
        if (detector != null)
            detector.setManufacturer(manufacturer);
    }

    public void setDetectorModel(String model, int instrumentIndex,
            int detectorIndex) {
        log.debug(String.format(
                "setDetectorModel[%s] instrumentIndex[%d] detectorIndex[%d]",
                model, instrumentIndex, detectorIndex));
        
        Instrument instrument = getInstrument(instrumentIndex);
        Detector detector = getDetector(instrument, detectorIndex); 
        if (detector != null)
            detector.setManufacturer(model);
    }

    public void setDetectorOffset(Float offset, int instrumentIndex,
            int detectorIndex) {
        log.debug(String.format(
                "setDetectorOffset[%f] instrumentIndex[%d] detectorIndex[%d]",
                offset, instrumentIndex, detectorIndex));
        
        Instrument instrument = getInstrument(instrumentIndex);
        Detector detector = getDetector(instrument, detectorIndex); 
        if (detector != null)
            detector.setOffsetValue(offset);
    }

    public void setDetectorSerialNumber(String serialNumber,
            int instrumentIndex, int detectorIndex) {
        log.debug(String.format(
                "setDetectorSerialNumber[%s] instrumentIndex[%d] detectorIndex[%d]",
                serialNumber, instrumentIndex, detectorIndex));
        
        Instrument instrument = getInstrument(instrumentIndex);
        Detector detector = getDetector(instrument, detectorIndex); 
        if (detector != null)
            detector.setSerialNumber(serialNumber);
    }

    public void setDetectorType(String type, int instrumentIndex,
            int detectorIndex) {
        log.debug(String.format(
                "setDetectorType[%s] instrumentIndex[%d] lightSourceIndex[%d]",
                type, instrumentIndex, detectorIndex));
        
        Instrument instrument = getInstrument(instrumentIndex);
        Detector detector = getDetector(instrument, detectorIndex); 
        if (detector != null)
            detector.setType((DetectorType) getEnumeration(DetectorType.class, type)); 
    }

    public void setDetectorVoltage(Float voltage, int instrumentIndex,
            int detectorIndex) {
        log.debug(String.format(
                "setDetectorOffset[%f] instrumentIndex[%d] detectorIndex[%d]",
                voltage, instrumentIndex, detectorIndex));
        
        Instrument instrument = getInstrument(instrumentIndex);
        Detector detector = getDetector(instrument, detectorIndex); 
        if (detector != null)
            detector.setVoltage(voltage);  
    }

    /* ---- Objective ---- */

    public void setObjectiveID(String id, int instrumentIndex,
            int objectiveIndex)
    {
        // Add this lsid to the lsidMap and set currentID = id.
        mapLSID(id);

        log.debug(String.format(
                "Mapping ObjectiveID[%s] InstrumentIndex[%d] detectorIndex[%d]",
                id, instrumentIndex, objectiveIndex));    
    }

    private Objective getObjective(Instrument instrument, int objectiveIndex)
    {
        if ((instrument.sizeOfDetector() - 1) < objectiveIndex)
        {
            Objective objective = new Objective();
            lsidMap.put(currentLSID, objective);
            instrument.addObjective(objective);
        } 

        return (Objective) lsidMap.get(currentLSID);
    }
    
    public void setObjectiveCalibratedMagnification(
            Float calibratedMagnification, int instrumentIndex, int objectiveIndex) 
    {
        log.debug(String.format(
                "setObjectiveCalibratedMagnification[%f] instrumentIndex[%d] detectorIndex[%d]",
                calibratedMagnification, instrumentIndex, objectiveIndex));
        
        Instrument instrument = getInstrument(instrumentIndex);
        Objective objective = getObjective(instrument, objectiveIndex); 
        if (objective != null && calibratedMagnification != null) // fix for older ome formats
            objective.setMagnificiation(calibratedMagnification.doubleValue());
    }

    public void setObjectiveImmersion(String immersion, int instrumentIndex,
            int objectiveIndex) {
        log.debug(String.format(
                "setObjectiveImmersion[%s] instrumentIndex[%d] detectorIndex[%d]",
                immersion, instrumentIndex, objectiveIndex));
        
        Instrument instrument = getInstrument(instrumentIndex);
        Objective objective = getObjective(instrument, objectiveIndex); 
        if (objective != null)
            objective.setImmersion((Immersion) getEnumeration(Immersion.class, immersion)); 
    }

    public void setObjectiveLensNA(Float lensNA, int instrumentIndex,
            int objectiveIndex) {
        log.debug(String.format(
                "setObjectiveLensNA[%f] instrumentIndex[%d] detectorIndex[%d]",
                lensNA, instrumentIndex, objectiveIndex));
        
        Instrument instrument = getInstrument(instrumentIndex);
        Objective objective = getObjective(instrument, objectiveIndex); 
        if (objective != null)
            objective.setLensNA(lensNA); 
    }

    public void setObjectiveManufacturer(String manufacturer,
            int instrumentIndex, int objectiveIndex) {
        log.debug(String.format(
                "setObjectiveManufacturer[%s] instrumentIndex[%d] detectorIndex[%d]",
                manufacturer, instrumentIndex, objectiveIndex));
        
        Instrument instrument = getInstrument(instrumentIndex);
        Objective objective = getObjective(instrument, objectiveIndex); 
        if (objective != null)
            objective.setManufacturer(manufacturer); 
    }

    public void setObjectiveModel(String model, int instrumentIndex,
            int objectiveIndex) {
        log.debug(String.format(
                "setObjectiveModel[%s] instrumentIndex[%d] detectorIndex[%d]",
                model, instrumentIndex, objectiveIndex));
        
        Instrument instrument = getInstrument(instrumentIndex);
        Objective objective = getObjective(instrument, objectiveIndex); 
        if (objective != null)
            objective.setModel(model); 
    }

    public void setObjectiveNominalMagnification(Integer nominalMagnification,
            int instrumentIndex, int objectiveIndex) 
    {
        log.debug(String.format(
                "setObjectiveNominalMagnification[%d] instrumentIndex[%d] detectorIndex[%d]",
                nominalMagnification, instrumentIndex, objectiveIndex));
        
        Instrument instrument = getInstrument(instrumentIndex);
        Objective objective = getObjective(instrument, objectiveIndex); 
        if (objective != null)
            objective.setMagnificiation(nominalMagnification.doubleValue());
    }

    public void setObjectiveSerialNumber(String serialNumber,
            int instrumentIndex, int objectiveIndex) 
    {
        log.debug(String.format(
                "setObjectiveSerialNumber[%s] instrumentIndex[%d] detectorIndex[%d]",
                serialNumber, instrumentIndex, objectiveIndex));
        
        Instrument instrument = getInstrument(instrumentIndex);
        Objective objective = getObjective(instrument, objectiveIndex); 
        if (objective != null)
            objective.setSerialNumber(serialNumber);
    }

    public void setObjectiveWorkingDistance(Float workingDistance,
            int instrumentIndex, int objectiveIndex) {
        log.debug(String.format(
                "setObjectiveWorkingDistance[%f] instrumentIndex[%d] detectorIndex[%d]",
                workingDistance, instrumentIndex, objectiveIndex));
        
        Instrument instrument = getInstrument(instrumentIndex);
        Objective objective = getObjective(instrument, objectiveIndex); 
        //if (objective != null)
        //    objective.set;
        
    }

    /* ---- OTF ---- */

    public void setOTFID(String id, int instrumentIndex, int otfIndex)
    {
        // Add this lsid to the lsidMap and set currentID = id.
        mapLSID(id);
        
        log.debug(String.format(
                "Mapping OTFID[%s] InstrumentIndex[%d] detectorIndex[%d]",
                id, instrumentIndex, otfIndex));
    }
    
    private OTF getOTF(Instrument instrument, int otfIndex)
    {
        if ((instrument.sizeOfDetector() - 1) < otfIndex)
        {
            OTF otf = new OTF();
            lsidMap.put(currentLSID, otf);
        } 

        return (OTF) lsidMap.get(currentLSID);
    }
    
    public void setOTFOpticalAxisAveraged(Boolean opticalAxisAveraged,
            int instrumentIndex, int otfIndex) {
        log.debug(String.format(
                "TODO: setOTFOpticalAxisAveraged[%b] instrumentIndex[%d] otfIndex[%d] ",
                opticalAxisAveraged, instrumentIndex, otfIndex));    
    }

    public void setOTFPath(String path, int instrumentIndex, int otfIndex) {
        log.debug(String.format(
                "TODO: setOTFOpticalAxisAveraged[%s] instrumentIndex[%d] otfIndex[%d] ",
                path, instrumentIndex, otfIndex));    
    }

    public void setOTFPixelType(String pixelType, int instrumentIndex,
            int otfIndex) {
        log.debug(String.format(
                "TODO: setOTFPixelType[%s] instrumentIndex[%d] otfIndex[%d] ",
                pixelType, instrumentIndex, otfIndex));        
    }

    public void setOTFSizeX(Integer sizeX, int instrumentIndex, int otfIndex) {
        log.debug(String.format(
                "TODO: setOTFSizeX[%d] instrumentIndex[%d] otfIndex[%d] ",
                sizeX, instrumentIndex, otfIndex));     
    }

    public void setOTFSizeY(Integer sizeY, int instrumentIndex, int otfIndex) {
        log.debug(String.format(
                "TODO: setOTFSizeY[%d] instrumentIndex[%d] otfIndex[%d] ",
                sizeY, instrumentIndex, otfIndex));    
    }

    /* ---- Restricted "Admin Only" methods ---- */

    public void setExperimenterDataDirectory(String dataDirectory,
            int experimenterIndex) {
        log.debug(String.format(
                "Admin only function: Ignoring dataDirectory[%s] experimenterIndex[%d] ",
                dataDirectory, experimenterIndex)); 
    }

    public void setExperimenterEmail(String email, int experimenterIndex) {
        log.debug(String.format(
                "Admin only function: Ignoring email[%s] experimenterIndex[%d] ",
                email, experimenterIndex)); 
    }

    public void setExperimenterFirstName(String firstName, int experimenterIndex) {
        log.debug(String.format(
                "Admin only function: Ignoring firstName[%s] experimenterIndex[%d] ",
                firstName, experimenterIndex)); 
    }

    public void setExperimenterInstitution(String institution,
            int experimenterIndex) {
        log.debug(String.format(
                "Admin only function: Ignoring institution[%s] experimenterIndex[%d] ",
                institution, experimenterIndex));
    }

    public void setExperimenterLastName(String lastName, int experimenterIndex) {
        log.debug(String.format(
                "Admin only function: Ignoring lastName[%s] experimenterIndex[%d] ",
                lastName, experimenterIndex)); 
    }


    /* ---- Methods that are skipped because OMERO sets its own values for these ---- */

    public void setDetectorNodeID(String nodeID, int instrumentIndex,
            int detectorIndex)
    {
        throw new RuntimeException("Not implemented yet.");
    }

    public void setDisplayOptionsNodeID(String nodeID, int imageIndex)
    {
        throw new RuntimeException("Not implemented yet.");
    }

    public void setExperimenterNodeID(String nodeID, int experimenterIndex)
    {
        throw new RuntimeException("Not implemented yet.");
    }

    public void setImageNodeID(String nodeID, int imageIndex)
    {
        throw new RuntimeException("Not implemented yet.");
    }

    public void setInstrumentNodeID(String nodeID, int instrumentIndex)
    {
        throw new RuntimeException("Not implemented yet.");
    }

    public void setLightSourceNodeID(String nodeID, int instrumentIndex,
            int lightSourceIndex)
    {
        throw new RuntimeException("Not implemented yet.");
    }

    public void setLogicalChannelNodeID(String nodeID, int imageIndex,
            int logicalChannelIndex)
    {
        throw new RuntimeException("Not implemented yet.");
    }

    public void setOTFNodeID(String nodeID, int instrumentIndex, int otfIndex)
    {
        throw new RuntimeException("Not implemented yet.");
    }

    public void setObjectiveNodeID(String nodeID, int instrumentIndex,
            int objectiveIndex)
    {
        throw new RuntimeException("Not implemented yet.");
    }

    public void setPixelsNodeID(String nodeID, int imageIndex, int pixelsIndex)
    {
        throw new RuntimeException("Not implemented yet.");
    }

    public void setROINodeID(String nodeID, int imageIndex, int roiIndex)
    {
        throw new RuntimeException("Not implemented yet.");
    }

    public void setDisplayOptionsID(String id, int imageIndex)
    {
        log.debug(String.format(
                "SKIPPED: setDisplayOptionsID[%s] imageIndex[%d] will be set by OMERO",
                id, imageIndex));
    }

    public void setExperimenterID(String id, int experimenterIndex)
    {
        log.debug(String.format(
                "SKIPPED: setExperimenterID[%s] experimenterIndex[%d] will be set by OMERO",
                id, experimenterIndex));
    }

    public void setImageID(String id, int imageIndex)
    {
        log.debug(String.format(
                "SKIPPED: setImageID[%s] imageIndex[%d] will be set by OMERO",
                id, imageIndex));
    }

    public void setInstrumentID(String id, int instrumentIndex)
    {
        log.debug(String.format(
                "SKIPPED: setInstrumentID[%s] instrumentIndex[%d] will be set by OMERO",
                id, instrumentIndex)); 
    }

    public void setLogicalChannelID(String id, int imageIndex,
            int logicalChannelIndex)
    {
        log.debug(String.format(
                "SKIPPED: setLogicalChannelID[%s] imageIndex[%d] logicalChannelIndex[%d] will be set by OMERO",
                id, imageIndex, logicalChannelIndex));
    }

    public void setPixelsID(String id, int imageIndex, int pixelsIndex)
    {
        log.debug(String.format(
                "SKIPPED: setPixelsID[%s] imageIndex[%d] pixelsIndex[%d] will be set by OMERO",
                id, imageIndex, pixelsIndex));
    }

    public void setROIID(String id, int imageIndex, int roiIndex)
    {
        log.debug(String.format(
                "SKIPPED: setROIID[%s] imageIndex[%d] rioIndex[%d] will be set by OMERO",
                id, imageIndex, roiIndex));
    }        

    public void setROIT0(Integer t0, int imageIndex, int roiIndex) {
        log.debug(String.format(
                "FIXME: Ignoring setROIT0[%s] imageIndex[%d] rioIndex[%d] ",
                t0, imageIndex, roiIndex)); 
    }

    public void setROIT1(Integer t1, int imageIndex, int roiIndex) {
        log.debug(String.format(
                "FIXME: Ignoring setROIT1[%s] imageIndex[%d] rioIndex[%d] ",
                t1, imageIndex, roiIndex));
    }

    public void setROIX0(Integer x0, int imageIndex, int roiIndex) {
        log.debug(String.format(
                "FIXME: Ignoring setROIX0[%s] imageIndex[%d] rioIndex[%d] ",
                x0, imageIndex, roiIndex));  
    }

    public void setROIX1(Integer x1, int imageIndex, int roiIndex) {
        log.debug(String.format(
                "FIXME: Ignoring setROIX1[%s] imageIndex[%d] rioIndex[%d] ",
                x1, imageIndex, roiIndex));        
    }

    public void setROIY0(Integer y0, int imageIndex, int roiIndex) {
        log.debug(String.format(
                "FIXME: Ignoring setROIY0[%s] imageIndex[%d] rioIndex[%d] ",
                y0, imageIndex, roiIndex));   
    }

    public void setROIY1(Integer y1, int imageIndex, int roiIndex) {
        log.debug(String.format(
                "FIXME: Ignoring setROIY1[%s] imageIndex[%d] rioIndex[%d] ",
                y1, imageIndex, roiIndex));  
    }

    public void setROIZ0(Integer z0, int imageIndex, int roiIndex) {
        log.debug(String.format(
                "FIXME: Ignoring setROIZ0[%s] imageIndex[%d] rioIndex[%d] ",
                z0, imageIndex, roiIndex));  
    }

    public void setROIZ1(Integer z1, int imageIndex, int roiIndex) {
        log.debug(String.format(
                "FIXME: Ignoring setROIZ1[%s] imageIndex[%d] rioIndex[%d] ",
                z1, imageIndex, roiIndex)); 
    }

    public void setDisplayOptionsProjectionZStart(Integer start, int imageIndex)
    {
        log.debug(String.format(
                "FIXME: Ignoring setDisplayOptionsProjectionZStart[%d] imageIndex[%d] ",
                start, imageIndex));
    }

    public void setDisplayOptionsProjectionZStop(Integer stop, int imageIndex)
    {
        log.debug(String.format(
                "FIXME: Ignoring setDisplayOptionsProjectionZStop[%d] imageIndex[%d] ",
                stop, imageIndex));
    }

    public void setDisplayOptionsTimeTStart(Integer start, int imageIndex)
    {
        log.debug(String.format(
                "FIXME: Ignoring setDisplayOptionsTimeTStart[%s] imageIndex[%d] ",
                start, imageIndex));
    }

    public void setDisplayOptionsTimeTStop(Integer stop, int imageIndex)
    {
        log.debug(String.format(
                "FIXME: Ignoring setDisplayOptionsTimeTStop[%s] imageIndex[%d] ",
                stop, imageIndex));
    }

    public void setDisplayOptionsZoom(Float zoom, int imageIndex)
    {
        log.debug(String.format(
                "FIXME: Ignoring setDisplayOptionsZoom[%f] imageIndex[%d] ",
                zoom, imageIndex));
    }

    public void setChannelComponentIndex(Integer index, int imageIndex,
            int logicalChannelIndex, int channelComponentIndex)
    {
        log.debug(String.format(
                "FIXME: Ignoring setChannelComponentIndex[%d] imageIndex[%d] " +
                "logicalChannelIndex [%d] channelComponentIndex[%d]",
                index, imageIndex, logicalChannelIndex, channelComponentIndex));
    }

    /* ---- Methods that are not represented in the OMERO model (and probably never will be) ---- */

    /* (non-Javadoc)
     * @see loci.formats.meta.MetadataStore#setPixelsBigEndian(java.lang.Boolean, int, int)
     */
    public void setPixelsBigEndian(Boolean bigEndian, int imageIndex, int pixelsIndex)
    {
        log.debug(String.format(
                "IGNORED: setPixelsBigEndian[%s] Image[%d] Pixels[%d] not represented in OMERO",
                bigEndian, imageIndex, pixelsIndex));
    }

    /* (non-Javadoc)
     * @see loci.formats.meta.MetadataStore#setLogicalChannelSamplesPerPixel(java.lang.Integer, int, int)
     */
    public void setLogicalChannelSamplesPerPixel(Integer samplesPerPixel,
            int imageIndex, int logicalChannelIndex)
    {
        Image image = getImage(imageIndex);
        Iterator<Pixels> i = image.iteratePixels();
        while (i.hasNext())
        {
            Pixels p = i.next();
            log.debug(String.format(
                    "IGNORED: setLogicalChannelSamplesPerPixel[%d] Image[%d] Pixels[%s] LogicalChannel[%d] not represented in OMERO",
                    samplesPerPixel, imageIndex, p, logicalChannelIndex));
        }
    }

    public void setTiffDataFileName(String fileName, int imageIndex,
            int pixelsIndex, int tiffDataIndex)
    {
        log.debug(String.format(
                "IGNORED: setTiffDataFileName[%s] imageIndex[%d] pixelsIndex[%d] not represented in OMERO",
                fileName, imageIndex, pixelsIndex));
    }

    public void setTiffDataFirstC(Integer firstC, int imageIndex,
            int pixelsIndex, int tiffDataIndex)
    {
        log.debug(String.format(
                "IGNORED: setTiffDataFirstC[%d] imageIndex[%d] pixelsIndex[%d] not represented in OMERO",
                firstC, imageIndex, pixelsIndex));
    }

    public void setTiffDataFirstT(Integer firstT, int imageIndex,
            int pixelsIndex, int tiffDataIndex)
    {
        log.debug(String.format(
                "IGNORED: setTiffDataFirstT[%d] imageIndex[%d] pixelsIndex[%d] not represented in OMERO",
                firstT, imageIndex, pixelsIndex));
    }

    public void setTiffDataFirstZ(Integer firstZ, int imageIndex,
            int pixelsIndex, int tiffDataIndex)
    {
        log.debug(String.format(
                "IGNORED: setTiffDataFirstZ[%d] imageIndex[%d] pixelsIndex[%d] not represented in OMERO",
                firstZ, imageIndex, pixelsIndex));
    }

    public void setTiffDataIFD(Integer ifd, int imageIndex, int pixelsIndex,
            int tiffDataIndex)
    {
        log.debug(String.format(
                "IGNORED: setTiffDataIFD[%d] imageIndex[%d] pixelsIndex[%d] not represented in OMERO",
                ifd, imageIndex, pixelsIndex));
    }

    public void setTiffDataNumPlanes(Integer numPlanes, int imageIndex,
            int pixelsIndex, int tiffDataIndex)
    {
        log.debug(String.format(
                "IGNORED: setTiffDataNumPlanes[%d] imageIndex[%d] pixelsIndex[%d] not represented in OMERO",
                numPlanes, imageIndex, pixelsIndex));
    }

    public void setTiffDataUUID(String uuid, int imageIndex, int pixelsIndex,
            int tiffDataIndex)
    {
        log.debug(String.format(
                "IGNORED: setROIID[%s] imageIndex[%d] pixelsIndex[%d] not represented in OMERO",
                uuid, imageIndex, pixelsIndex));
    }

    /* ---- Methods that are missing from the data model and need to be added ---- */

    /* ---- PixelsDimenions ---- */

    /* (non-Javadoc)
     * @see loci.formats.meta.MetadataStore#setDimensionsTimeIncrement(java.lang.Float, int, int)
     */
    public void setDimensionsTimeIncrement(Float timeIncrement, int imageIndex, int pixelsIndex)
    {
        log.debug(String.format(
                "Setting Image[%d] Pixels[%d] time increment: '%f'",
                imageIndex, pixelsIndex, timeIncrement));
        log.debug("NOTE: This field is unsupported/unused.");        
    }

    /* (non-Javadoc)
     * @see loci.formats.meta.MetadataStore#setDimensionsWaveIncrement(java.lang.Integer, int, int)
     */
    public void setDimensionsWaveIncrement(Integer waveIncrement, int imageIndex, int pixelsIndex)
    {
        log.debug(String.format(
                "Setting Image[%d] Pixels[%d] wave increment: '%d'",
                imageIndex, pixelsIndex, waveIncrement));
        log.debug("NOTE: This field is unsupported/unused.");
    }

    /* (non-Javadoc)
     * @see loci.formats.meta.MetadataStore#setDimensionsWaveStart(java.lang.Integer, int, int)
     */
    public void setDimensionsWaveStart(Integer waveStart, int imageIndex, int pixelsIndex)
    {
        log.debug(String.format(
                "Setting Image[%d] Pixels[%d] wave start: '%d'",
                imageIndex, pixelsIndex, waveStart));
        log.debug("NOTE: This field is unsupported/unused.");
    }

    public void setChannelComponentColorDomain(String colorDomain,
            int imageIndex, int logicalChannelIndex, int channelComponentIndex)
    {
        log.debug(String.format(
                "FIXME: Ignoring setChannelComponentColorDomain[%s] imageIndex[%d] " +
                "logicalChannelIndex [%d] channelComponentIndex[%d]",
                colorDomain, imageIndex, logicalChannelIndex, channelComponentIndex));
    }
    

    public void setObjectiveCorrection(String correction, int instrumentIndex,
            int objectiveIndex) 
    {
        log.debug(String.format(
                "setObjectiveCorrection[%s] instrumentIndex[%d] detectorIndex[%d]",
                correction, instrumentIndex, objectiveIndex));
        
        Instrument instrument = getInstrument(instrumentIndex);
        Objective objective = getObjective(instrument, objectiveIndex); 
        //if (objective != null)
            // needs to be added
    }

    
    /* ------ new just before beta 3 release from curtis - not handled today ----- */
    
    public void setPlateDescription(String description, int plateIndex)
    {
        log.debug(String.format(
                "ignoring setPlateDescription"));
    }

    public void setPlateExternalIdentifier(String externalIdentifier,
            int plateIndex)
    {
        log.debug(String.format(
        "ignoring setPlateExternalIdentifier"));
    }

    public void setPlateID(String id, int plateIndex)
    {
        log.debug(String.format(
        "ignoring setPlateID"));
    }

    public void setPlateName(String name, int plateIndex)
    {
        log.debug(String.format(
        "ignoring setPlateName"));
    }

    public void setPlateRefID(String id, int screenIndex, int plateRefIndex)
    {
        log.debug(String.format(
        "ignoring setPlateRefID"));
    }

    public void setPlateStatus(String status, int plateIndex)
    {
        log.debug(String.format(
        "ignoring setPlateStatus"));
    }

    public void setReagentDescription(String description, int screenIndex,
            int reagentIndex)
    {
        log.debug(String.format(
        "ignoring setReagentDescription"));
    }

    public void setReagentID(String id, int screenIndex, int reagentIndex)
    {
        log.debug(String.format(
        "ignoring setReagentID"));
    }

    public void setReagentName(String name, int screenIndex, int reagentIndex)
    {
        log.debug(String.format(
        "ignoring setReagentName"));
    }

    public void setReagentReagentIdentifier(String reagentIdentifier,
            int screenIndex, int reagentIndex)
    {
        log.debug(String.format(
        "ignoring setReagentReagentIdentifier"));
    }

    public void setScreenAcquisitionEndTime(String endTime, int screenIndex,
            int screenAcquisitionIndex)
    {
        log.debug(String.format(
        "ignoring setScreenAcquisitionEndTime"));
    }

    public void setScreenAcquisitionID(String id, int screenIndex,
            int screenAcquisitionIndex)
    {
        log.debug(String.format(
        "ignoring setPlateDescription"));
    }

    public void setScreenAcquisitionStartTime(String startTime,
            int screenIndex, int screenAcquisitionIndex)
    {
        log.debug(String.format(
        "ignoring setPlateDescription"));
    }

    public void setScreenID(String id, int screenIndex)
    {
        log.debug(String.format(
        "ignoring setPlateDescription"));
    }

    public void setScreenName(String name, int screenIndex)
    {
        log.debug(String.format(
        "ignoring setPlateDescription"));
    }

    public void setScreenProtocolDescription(String protocolDescription,
            int screenIndex)
    {
        log.debug(String.format(
        "ignoring setPlateDescription"));
    }

    public void setScreenProtocolIdentifier(String protocolIdentifier,
            int screenIndex)
    {
        log.debug(String.format(
        "ignoring setPlateDescription"));
    }

    public void setScreenReagentSetDescription(String reagentSetDescription,
            int screenIndex)
    {
        log.debug(String.format(
        "ignoring setScreenReagentSetDescription"));
    }

    public void setScreenType(String type, int screenIndex)
    {
        log.debug(String.format(
        "ignoring setScreenType"));
    }

    public void setUUID(String uuid)
    {
        log.debug(String.format(
        "ignoring setUUID"));
    }

    public void setWellColumn(Integer column, int wellIndex)
    {
        log.debug(String.format(
        "ignoring setWellColumn"));
    }

    public void setWellExternalDescription(String externalDescription,
            int wellIndex)
    {
        log.debug(String.format(
        "ignoring setWellExternalDescription"));
    }

    public void setWellExternalIdentifier(String externalIdentifier,
            int wellIndex)
    {
        log.debug(String.format(
        "ignoring setWellExternalIdentifier"));
    }

    public void setWellID(String id, int wellIndex)
    {
        log.debug(String.format(
        "ignoring setWellID"));
    }

    public void setWellRow(Integer row, int wellIndex)
    {
        log.debug(String.format(
        "ignoring setWellRow"));
    }

    public void setWellSampleID(String id, int wellIndex, int wellSampleIndex)
    {
        log.debug(String.format(
        "ignoring setWellSampleID"));
    }

    public void setWellSampleIndex(Integer index, int wellIndex,
            int wellSampleIndex)
    {
        log.debug(String.format(
        "ignoring setWellSampleIndex"));
    }

    public void setWellSamplePosX(Float posX, int wellIndex, int wellSampleIndex)
    {
        log.debug(String.format(
        "ignoring setWellSamplePosX"));
    }

    public void setWellSamplePosY(Float posY, int wellIndex, int wellSampleIndex)
    {
        log.debug(String.format(
        "ignoring setPlateDescription"));
    }

    public void setWellSampleTimepoint(Integer timepoint, int wellIndex,
            int wellSampleIndex)
    {
        log.debug(String.format(
        "ignoring setWellSamplePosY"));
    }

    public void setWellType(String type, int wellIndex)
    {
        log.debug(String.format(
        "ignoring setWellType"));
    }

    public void setImageInstrumentRef(String instrumentRef, int imageIndex)
    {
        log.debug(String.format(
        "ignoring setImageInstrumentRef"));
    }
    
    // Bio-formats also needs a way to link OTF to Logical Channel
}
