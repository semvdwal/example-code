<?php
//
// @(#)$HeadURL$ $Date$
//
// Copyright 1999-2013(c) MijnWinkel B.V. Rijnegomlaan 33, Aerdenhout,
// North Holland, NL-2114EH, The Netherlands All rights reserved.
//
// This software is the confidential and proprietary information of MijnWinkel
// B.V. ("Confidential Information"). You shall not disclose such Confidential
// Information and shall use it only in accordance with the terms of the license
// agreement you entered into with MijnWinkel.
//
//
//
// Class for translating myshop xml using sax parser
//
// @version $Revision$ $Author$
// @since 20-07-2015
// @author Sem van der Wal
//
//

class MyshopXMLSAXReader {

    private $fileName = null;
    private $parser = null;

    private $productData;

    private $currentElement;
    private $rowType;
    private $columnNumber;
    private $columnNames = array();
    private $columnName;
    private $columnData;

    /** @var $productlist ProductList */
    private $productlist = null;
    private $products = array();

    private $groupField = null;
    private $domainFields = array();

    private $groups = array();
    private $currentGroup = null;
    private $currentGroupId = null;

    // Iterator variables
    /** @var Product $currentProduct */
    private $currentProduct = null;
    private $fileHandle = null;

    private $mode = 'get';
    private $batchStarted = false;
    private $productCount = 0;

    /**
     * Constructs the class and initializes it with the given xml filename
     * @param $fileName String The full path and name of the file to read
     * @param $productList ProductList The productlist to use
     * @throws Exception when the given filename is not valid
     */
    public function __construct($fileName, $productList){
        if(empty($fileName)){
            throw new Exception("SAXReader needs a filename to process");
        }
        if(!is_file($fileName)){
            throw new Exception("SAXReader called with a non-existing filename");
        }
        if($productList==null){
            throw new Exception("Cannot create products xml reader without productlist info");
        }

        $this->productlist = $productList;

        $size = filesize($fileName);
        // TODO: Instead of skipping utf-8 conversion, make the conversion works for larger files too
        if($size && $size < 100 * 1024 * 1024) {
            $xml = file_get_contents($fileName);
            $xml = Encoding::fixUTF8($xml);
            file_put_contents($fileName, $xml);
        }else{
            Logger::warn("File to large to convert to utf-8: ".$fileName.", size: ".$size);
        }

        $this->fileName = $fileName;

        $this->parser = xml_parser_create('utf-8');
        xml_set_element_handler($this->parser, array($this, 'startElement'), array($this, 'endElement'));
        xml_set_character_data_handler($this->parser, array($this, 'appendData'));
    }

    /**
     * Retrieves the products from the known xml file (given on construct), use the given productlist
     * @param $productlist ProductList The productlist to use to add the products to
     * @return Product[] An array of products found in the xml file
     * @throws Exception When the productlist is null
     */
    public function getProducts($productlist){

        if($productlist == null){
            throw new Exception("Cannot get products without productlist info");
        }

        $this->productlist = $productlist;

        $this->groupField = $productlist->getFieldsByType("is_variations_group_column");
        if(!empty($this->groupField)){
            $this->groupField = $this->groupField[0];
            $this->groupField = $this->groupField->name;
        }else{
            $this->groupField = "";
        }
        $this->domainFields = $productlist->getFieldsByType('is_menu_column', 'is_list_search_column');

        Logger::debug("Found domain fields: ");
        foreach($this->domainFields as $field){
            Logger::debug("- ".$field->name);
        }

        $this->parseFile($this->fileName);
        return $this->products;
    }

    /**
     * @param $productlist
     * @return int The amount of products added
     * @throws Exception
     */
    public function saveProducts($productlist){
        $this->mode = 'save';
        $this->getProducts($productlist);

        if($this->currentProduct) {
            $this->currentProduct->endInsertBatch();
        }else{
            Logger::warn("Current product was empty, count: ".count($this->products));
        }

        Logger::debug("Products array count: ".count($this->products));

        return $this->productCount;
    }

    /**
     * Gets the next product from xml
     * @return Product|false
     * @throws Exception
     */
    public function getNext(){
        $this->currentProduct = null;
        $this->parseUntilNext();
        if($this->currentProduct != null) {
            return $this->currentProduct;
        }
        return false;
    }

    /**
     * Reads the xml file and handles the parser
     * @throws Exception when the file cannot be opened
     */
    private function parseUntilNext(){
        if($this->fileHandle == null){
            $this->fileHandle = fopen($this->fileName, 'r');
        }

        if($this->fileHandle == false){
            throw new Exception("XML File could not be opened!");
        }

        while ($this->currentProduct == null && ($data = fread($this->fileHandle, 4096))) {
            $ok = xml_parse($this->parser, $data, feof($this->fileHandle));
            if(!$ok) {
                Logger::error(sprintf('XML ERROR: %s at line %d', xml_error_string(xml_get_error_code($this->parser)), xml_get_current_line_number($this->parser)));
                break;
            }
        }

        if(feof($this->fileHandle)) {
            fclose($this->fileHandle);
            xml_parser_free($this->parser);
        }
    }

    /**
     * Reads the xml file and handles the parser
     * @param $fileName String The filename of the file to read
     * @throws Exception when the file cannot be opened
     */
    private function parseFile($fileName){
        $fp = fopen($fileName, 'r');

        if($fp == false){
            throw new Exception("XML File could not be opened!");
        }

        while ($data = fread($fp, 4096)) {
            $ok = xml_parse($this->parser, $data, feof($fp));
            if(!$ok) {
                Logger::error(sprintf('XML ERROR: %s at line %d', xml_error_string(xml_get_error_code($this->parser)), xml_get_current_line_number($this->parser)));
                break;
            }
        }

        fclose($fp);
        xml_parser_free($this->parser);
    }

    /**
     * Adds a new product from xml data to the product array
     * @param $productData
     */
    public function addProduct($productData){

        foreach($this->domainFields as $domainField){
            /** @var Field $domainField */
            $domainField->setDomainValue($productData[$domainField->name]);
        }

        if(!empty($this->groupField)){
            if($productData[$this->groupField] == $this->currentGroup && !empty($this->currentGroup)){
                $productData["_variant"] = 1;
            }else{
                $productData["_variant"] = 0;
                $this->currentGroup = $productData[$this->groupField];
                $this->currentGroupId = uniqid("groupid_");
            }
            $productData["_groupId"] = $this->currentGroupId;
        }

        if($this->mode == 'get'){
            $this->products[] = new Product($productData, $this->productlist->shopid, $this->productlist->cid, $this->productlist->fields);
        }elseif($this->mode == 'save'){
            $this->currentProduct = new Product($productData, $this->productlist->shopid, $this->productlist->cid, $this->productlist->fields);
            if(!$this->batchStarted){
                $this->currentProduct->startInsertBatch();
                $this->batchStarted = true;
            }
            $this->currentProduct->_id = new MongoId();
            $this->currentProduct->updateCategoriesFromFields($this->productlist);
            if($this->currentProduct->validate()) {
                $this->currentProduct->save();
                $this->productCount++;
            }else{
                Logger::warn("Could not save product ".$this->currentProduct->_shopid."::".$this->currentProduct->_productid.": ".$this->currentProduct->_validationMessage);
            }

            if($this->productCount % 1000 == 0){
                Logger::memory();
            }
            /*
            if($this->productCount == 100 || $this->productCount == 10000 || $this->productCount == 5000){
                foreach(get_object_vars($this) as $varName => $varValue){
                    Logger::debug("Var ".$varName." : ".Logger::sizeofvar($varValue));
                }
            }
            */
        }else{
            $this->currentProduct = new Product($productData, $this->productlist->shopid, $this->productlist->cid, $this->productlist->fields);
        }
    }

    /**
     * This function is called by the xml parser only!
     * Handles start of an element
     * @param $parser Resource The current parser
     * @param $name String The name of the opened element (uppercase)
     * @param $attributes String[] Associative array of the attributes in this element (attribute names are uppercase)
     */
    public function startElement($parser, $name, $attributes){
        $this->currentElement = $name;

        switch($name){
            case 'ROW':
                $this->rowType = $attributes['TYPE'];
                break;
            case 'COL':
                $this->columnNumber = intval($attributes['NUMBER']);
                break;
            case 'NAME':
                $this->columnName = '';
                break;
            case 'PROP':
                break;

        }

    }

    /**
     * This function is called by the xml parser only!
     * Handles closing of a xml tag
     * @param $parser Resource The current xml parser
     * @param $name String the name of the closed element (uppercase)
     */
    public function endElement($parser, $name){

        switch($name){
            case 'ROW':
                if($this->rowType == 'data'){
                    $this->addProduct($this->productData);
                }
                break;
            case 'COL':
                if($this->rowType == 'column_set'){
                    $this->columnNames[$this->columnNumber] = trim($this->columnName);
                }else if($this->rowType == 'data'){
                    $this->productData[$this->columnNames[$this->columnNumber]] = trim($this->columnData);
                    $this->columnNumber = null;
                    $this->columnData = '';
                }
                break;
            case 'NAME':
                $this->columnName = $this->columnData;
                $this->columnData = '';
                break;
            case 'PROP':
                break;
        }
    }

    /**
     * This function is called by the xml parser only!
     * CDATA handler - appends data to the current data value
     * @param $parser Resource The current xml parser
     * @param $data String The data to append
     */
    public function appendData($parser, $data){
        if($this->currentElement == 'NAME' || $this->currentElement == 'COL') {
            $this->columnData .= $data;
        }
    }

}