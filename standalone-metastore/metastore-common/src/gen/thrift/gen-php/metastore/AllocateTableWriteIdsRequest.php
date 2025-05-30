<?php
namespace metastore;

/**
 * Autogenerated by Thrift Compiler (0.16.0)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */
use Thrift\Base\TBase;
use Thrift\Type\TType;
use Thrift\Type\TMessageType;
use Thrift\Exception\TException;
use Thrift\Exception\TProtocolException;
use Thrift\Protocol\TProtocol;
use Thrift\Protocol\TBinaryProtocolAccelerated;
use Thrift\Exception\TApplicationException;

class AllocateTableWriteIdsRequest
{
    static public $isValidate = false;

    static public $_TSPEC = array(
        1 => array(
            'var' => 'dbName',
            'isRequired' => true,
            'type' => TType::STRING,
        ),
        2 => array(
            'var' => 'tableName',
            'isRequired' => true,
            'type' => TType::STRING,
        ),
        3 => array(
            'var' => 'txnIds',
            'isRequired' => false,
            'type' => TType::LST,
            'etype' => TType::I64,
            'elem' => array(
                'type' => TType::I64,
                ),
        ),
        4 => array(
            'var' => 'replPolicy',
            'isRequired' => false,
            'type' => TType::STRING,
        ),
        5 => array(
            'var' => 'srcTxnToWriteIdList',
            'isRequired' => false,
            'type' => TType::LST,
            'etype' => TType::STRUCT,
            'elem' => array(
                'type' => TType::STRUCT,
                'class' => '\metastore\TxnToWriteId',
                ),
        ),
        6 => array(
            'var' => 'reallocate',
            'isRequired' => false,
            'type' => TType::BOOL,
        ),
    );

    /**
     * @var string
     */
    public $dbName = null;
    /**
     * @var string
     */
    public $tableName = null;
    /**
     * @var int[]
     */
    public $txnIds = null;
    /**
     * @var string
     */
    public $replPolicy = null;
    /**
     * @var \metastore\TxnToWriteId[]
     */
    public $srcTxnToWriteIdList = null;
    /**
     * @var bool
     */
    public $reallocate = false;

    public function __construct($vals = null)
    {
        if (is_array($vals)) {
            if (isset($vals['dbName'])) {
                $this->dbName = $vals['dbName'];
            }
            if (isset($vals['tableName'])) {
                $this->tableName = $vals['tableName'];
            }
            if (isset($vals['txnIds'])) {
                $this->txnIds = $vals['txnIds'];
            }
            if (isset($vals['replPolicy'])) {
                $this->replPolicy = $vals['replPolicy'];
            }
            if (isset($vals['srcTxnToWriteIdList'])) {
                $this->srcTxnToWriteIdList = $vals['srcTxnToWriteIdList'];
            }
            if (isset($vals['reallocate'])) {
                $this->reallocate = $vals['reallocate'];
            }
        }
    }

    public function getName()
    {
        return 'AllocateTableWriteIdsRequest';
    }


    public function read($input)
    {
        $xfer = 0;
        $fname = null;
        $ftype = 0;
        $fid = 0;
        $xfer += $input->readStructBegin($fname);
        while (true) {
            $xfer += $input->readFieldBegin($fname, $ftype, $fid);
            if ($ftype == TType::STOP) {
                break;
            }
            switch ($fid) {
                case 1:
                    if ($ftype == TType::STRING) {
                        $xfer += $input->readString($this->dbName);
                    } else {
                        $xfer += $input->skip($ftype);
                    }
                    break;
                case 2:
                    if ($ftype == TType::STRING) {
                        $xfer += $input->readString($this->tableName);
                    } else {
                        $xfer += $input->skip($ftype);
                    }
                    break;
                case 3:
                    if ($ftype == TType::LST) {
                        $this->txnIds = array();
                        $_size765 = 0;
                        $_etype768 = 0;
                        $xfer += $input->readListBegin($_etype768, $_size765);
                        for ($_i769 = 0; $_i769 < $_size765; ++$_i769) {
                            $elem770 = null;
                            $xfer += $input->readI64($elem770);
                            $this->txnIds []= $elem770;
                        }
                        $xfer += $input->readListEnd();
                    } else {
                        $xfer += $input->skip($ftype);
                    }
                    break;
                case 4:
                    if ($ftype == TType::STRING) {
                        $xfer += $input->readString($this->replPolicy);
                    } else {
                        $xfer += $input->skip($ftype);
                    }
                    break;
                case 5:
                    if ($ftype == TType::LST) {
                        $this->srcTxnToWriteIdList = array();
                        $_size771 = 0;
                        $_etype774 = 0;
                        $xfer += $input->readListBegin($_etype774, $_size771);
                        for ($_i775 = 0; $_i775 < $_size771; ++$_i775) {
                            $elem776 = null;
                            $elem776 = new \metastore\TxnToWriteId();
                            $xfer += $elem776->read($input);
                            $this->srcTxnToWriteIdList []= $elem776;
                        }
                        $xfer += $input->readListEnd();
                    } else {
                        $xfer += $input->skip($ftype);
                    }
                    break;
                case 6:
                    if ($ftype == TType::BOOL) {
                        $xfer += $input->readBool($this->reallocate);
                    } else {
                        $xfer += $input->skip($ftype);
                    }
                    break;
                default:
                    $xfer += $input->skip($ftype);
                    break;
            }
            $xfer += $input->readFieldEnd();
        }
        $xfer += $input->readStructEnd();
        return $xfer;
    }

    public function write($output)
    {
        $xfer = 0;
        $xfer += $output->writeStructBegin('AllocateTableWriteIdsRequest');
        if ($this->dbName !== null) {
            $xfer += $output->writeFieldBegin('dbName', TType::STRING, 1);
            $xfer += $output->writeString($this->dbName);
            $xfer += $output->writeFieldEnd();
        }
        if ($this->tableName !== null) {
            $xfer += $output->writeFieldBegin('tableName', TType::STRING, 2);
            $xfer += $output->writeString($this->tableName);
            $xfer += $output->writeFieldEnd();
        }
        if ($this->txnIds !== null) {
            if (!is_array($this->txnIds)) {
                throw new TProtocolException('Bad type in structure.', TProtocolException::INVALID_DATA);
            }
            $xfer += $output->writeFieldBegin('txnIds', TType::LST, 3);
            $output->writeListBegin(TType::I64, count($this->txnIds));
            foreach ($this->txnIds as $iter777) {
                $xfer += $output->writeI64($iter777);
            }
            $output->writeListEnd();
            $xfer += $output->writeFieldEnd();
        }
        if ($this->replPolicy !== null) {
            $xfer += $output->writeFieldBegin('replPolicy', TType::STRING, 4);
            $xfer += $output->writeString($this->replPolicy);
            $xfer += $output->writeFieldEnd();
        }
        if ($this->srcTxnToWriteIdList !== null) {
            if (!is_array($this->srcTxnToWriteIdList)) {
                throw new TProtocolException('Bad type in structure.', TProtocolException::INVALID_DATA);
            }
            $xfer += $output->writeFieldBegin('srcTxnToWriteIdList', TType::LST, 5);
            $output->writeListBegin(TType::STRUCT, count($this->srcTxnToWriteIdList));
            foreach ($this->srcTxnToWriteIdList as $iter778) {
                $xfer += $iter778->write($output);
            }
            $output->writeListEnd();
            $xfer += $output->writeFieldEnd();
        }
        if ($this->reallocate !== null) {
            $xfer += $output->writeFieldBegin('reallocate', TType::BOOL, 6);
            $xfer += $output->writeBool($this->reallocate);
            $xfer += $output->writeFieldEnd();
        }
        $xfer += $output->writeFieldStop();
        $xfer += $output->writeStructEnd();
        return $xfer;
    }
}
